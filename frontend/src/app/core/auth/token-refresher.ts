import { Injectable, inject } from '@angular/core';
import { Router } from '@angular/router';
import { Observable, catchError, finalize, map, retry, shareReplay, tap, throwError, timer } from 'rxjs';

import { AuthApi } from '../api/auth.api';
import { ApiError } from '../models/common.models';
import { AuthStore } from './auth.store';

/** Tab khác vừa refresh: đợi chút cho trình duyệt nhận cookie mới rồi thử lại. */
const RACE_RETRY_DELAY_MS = 300;

/**
 * [THÊM] Lấy access token mới bằng refresh token (cookie HttpOnly, trình duyệt tự gửi).
 *
 *   200                → lưu phiên mới, trả access token
 *   409 REFRESH_RACE   → tab khác vừa xoay token; đợi 300ms, thử lại MỘT lần (lúc này đã mang cookie mới)
 *   401 (mọi mã)       → phiên đã hết / bị thu hồi → xóa phiên, về trang đăng nhập
 *   mất mạng (0)       → KHÔNG đăng xuất, chỉ báo lỗi cho request đang chờ
 *
 * Nhiều request cùng gặp access token hết hạn (vd trang Hôm nay gọi 3 API một lúc) chỉ gây ra
 * MỘT lần gọi /refresh: cả ba cùng chờ chung một Observable (shareReplay). Nếu mỗi request tự refresh,
 * request thứ hai sẽ gửi cookie mà request thứ nhất vừa xoay → 409, thậm chí bị coi là dùng lại token.
 */
@Injectable({ providedIn: 'root' })
export class TokenRefresher {
  private readonly api = inject(AuthApi);
  private readonly store = inject(AuthStore);
  private readonly router = inject(Router);

  /** Lần refresh đang chạy (nếu có). Hết chạy thì về null để lần sau gọi lại server. */
  private inFlight: Observable<string> | null = null;

  refresh(): Observable<string> {
    this.inFlight ??= this.api.refresh().pipe(
      retry({
        count: 1,
        delay: (error: unknown) =>
          isRace(error) ? timer(RACE_RETRY_DELAY_MS) : throwError(() => error),
      }),
      tap((response) => this.store.setSession(response)),
      map((response) => response.accessToken),
      catchError((error: unknown) => {
        if (error instanceof ApiError && error.status !== 0) {
          this.endSession();
        }
        return throwError(() => error);
      }),
      finalize(() => (this.inFlight = null)),
      // refCount: false — người gọi đầu tiên hủy đăng ký thì request /refresh vẫn chạy tiếp cho những người còn chờ.
      shareReplay({ bufferSize: 1, refCount: false }),
    );
    return this.inFlight;
  }

  private endSession(): void {
    if (!this.store.isLoggedIn()) {
      return; // đã xử lý rồi (vd hai lần refresh liên tiếp cùng thất bại)
    }
    this.store.clear();
    void this.router.navigate(['/login'], { queryParams: { returnUrl: this.router.url, reason: 'expired' } });
  }
}

function isRace(error: unknown): boolean {
  return error instanceof ApiError && error.status === 409 && error.code === 'REFRESH_RACE';
}
