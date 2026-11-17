import { HttpContextToken, HttpErrorResponse, HttpInterceptorFn, HttpRequest } from '@angular/common/http';
import { inject } from '@angular/core';
import { Router } from '@angular/router';
import { catchError, switchMap, throwError } from 'rxjs';

import { APP_ENV } from '../config/app-env';
import { ApiError, ErrorResponse } from '../models/common.models';
import { AuthStore } from './auth.store';
import { TokenRefresher } from './token-refresher';

/** [THÊM] Đánh dấu request đã được gửi lại sau khi refresh: gặp 401 lần nữa thì thôi, không lặp vô hạn. */
const RETRIED_AFTER_REFRESH = new HttpContextToken<boolean>(() => false);

/** /api/auth/** (login, register, refresh, logout): không gắn token, không tự refresh. */
const isAuthCall = (req: HttpRequest<unknown>) => req.url.startsWith(`${APP_ENV.apiBaseUrl}/auth/`);
const isApiCall = (req: HttpRequest<unknown>) => req.url.startsWith(APP_ENV.apiBaseUrl);
const withToken = (req: HttpRequest<unknown>, token: string) =>
  req.clone({ setHeaders: { Authorization: `Bearer ${token}` } });

/**
 * Gắn "Authorization: Bearer <token>" vào mọi request gửi tới API của Cassin.
 *
 * [SỬA] Refresh CHỦ ĐỘNG: access token đã (sắp) hết hạn thì lấy token mới TRƯỚC khi gửi,
 * khỏi tốn một lượt request chắc chắn bị 401. errorInterceptor vẫn bắt 401 làm lưới an toàn
 * (đồng hồ máy người dùng lệch, token bị thu hồi phía server...).
 */
export const authInterceptor: HttpInterceptorFn = (req, next) => {
  const store = inject(AuthStore);
  const token = store.token();
  if (!token || !isApiCall(req) || isAuthCall(req)) {
    return next(req);
  }
  if (store.accessTokenExpired()) {
    return inject(TokenRefresher)
      .refresh()
      .pipe(switchMap((fresh) => next(withToken(req, fresh))));
  }
  return next(withToken(req, token));
};

/**
 * Đổi mọi lỗi HTTP thành ApiError (cùng kiểu với mock).
 *
 * [SỬA] API trả 401 → KHÔNG đăng xuất ngay nữa: refresh rồi gửi lại request đó MỘT lần.
 *   - refresh thành công → người dùng không thấy gì
 *   - refresh thất bại   → TokenRefresher xóa phiên, về trang đăng nhập
 * Không áp dụng cho /api/auth/**: ở đó 401 nghĩa là sai mật khẩu / refresh token hỏng, nơi gọi tự xử lý.
 */
export const errorInterceptor: HttpInterceptorFn = (req, next) => {
  const store = inject(AuthStore);
  const refresher = inject(TokenRefresher);
  const router = inject(Router);

  return next(req).pipe(
    catchError((error: unknown) => {
      const apiError = toApiError(error);
      if (!apiError) {
        return throwError(() => error);
      }
      if (apiError.status !== 401 || isAuthCall(req)) {
        return throwError(() => apiError);
      }
      if (!store.isLoggedIn()) {
        return throwError(() => apiError);
      }
      if (req.context.get(RETRIED_AFTER_REFRESH)) {
        // Vừa refresh xong mà vẫn 401: phiên có vấn đề thật → đăng xuất.
        store.clear();
        void router.navigate(['/login'], { queryParams: { returnUrl: router.url, reason: 'expired' } });
        return throwError(() => apiError);
      }
      return refresher.refresh().pipe(
        switchMap((fresh) => {
          const retried = withToken(req, fresh).clone({ context: req.context.set(RETRIED_AFTER_REFRESH, true) });
          // Request gửi lại đi thẳng tới server (next), nên phải tự đổi lỗi của nó sang ApiError.
          return next(retried).pipe(catchError((retryError: unknown) => throwError(() => toApiError(retryError) ?? retryError)));
        }),
      );
    }),
  );
};

/** HttpErrorResponse → ApiError. Lỗi không phải HTTP thì trả null (để nguyên cho nơi gọi). */
function toApiError(error: unknown): ApiError | null {
  if (!(error instanceof HttpErrorResponse)) {
    return error instanceof ApiError ? error : null;
  }
  if (error.status === 0) {
    return ApiError.network();
  }
  const body = error.error as Partial<ErrorResponse> | null;
  return new ApiError(
    error.status,
    body?.code ?? 'HTTP_' + error.status,
    body?.message ?? 'Đã có lỗi xảy ra. Vui lòng thử lại.',
    body?.fieldErrors ?? {},
  );
}
