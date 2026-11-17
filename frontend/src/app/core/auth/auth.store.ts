import { Injectable, computed, signal } from '@angular/core';

import { LoginResponse, User } from '../models/user.models';

/** Phiên đăng nhập lưu trong trình duyệt. */
interface Session {
  /** Access token (JWT). Refresh token KHÔNG nằm ở đây: nó ở cookie HttpOnly, JavaScript không đọc được. */
  token: string;
  /** Thời điểm ACCESS TOKEN hết hạn (ms, Date.now()). Hết hạn ≠ đăng xuất: còn refresh token thì lấy token mới. */
  expiresAt: number;
  user: User;
}

const STORAGE_KEY = 'cassin.session';

/**
 * Nơi DUY NHẤT giữ trạng thái đăng nhập của cả app (token + người dùng), dạng signal.
 *
 * Access token lưu ở localStorage để tải lại trang không bị đăng xuất.
 * Lưu ý bảo mật: localStorage đọc được bằng JavaScript, nên nếu trang bị chèn script lạ (XSS)
 * thì access token có thể bị lấy. [SỬA] Đánh đổi chấp nhận được vì access token chỉ sống ngắn,
 * còn refresh token (sống 14 ngày) nằm trong cookie HttpOnly mà script không đọc được.
 *
 * [SỬA] "Đã đăng nhập" giờ nghĩa là CÓ PHIÊN, kể cả khi access token đã hết hạn:
 * TokenRefresher sẽ dùng refresh token để lấy token mới. Chỉ khi refresh thất bại (401) mới xóa phiên.
 *
 * Mọi thao tác với localStorage đều bọc try/catch: trình duyệt ẩn danh hoặc chặn lưu trữ sẽ ném lỗi.
 */
@Injectable({ providedIn: 'root' })
export class AuthStore {
  private readonly session = signal<Session | null>(readSession());

  readonly user = computed(() => this.session()?.user ?? null);
  readonly token = computed(() => this.session()?.token ?? null);
  readonly isAdmin = computed(() => this.user()?.role === 'ADMIN');

  /** [SỬA] hasValidSession → isLoggedIn: có phiên là đã đăng nhập, không xét hạn access token nữa. */
  readonly isLoggedIn = computed(() => this.session() !== null);

  /**
   * [THÊM] Access token đã (hoặc sắp) hết hạn chưa. Gọi như hàm vì phụ thuộc thời gian.
   * Trừ hao `skewMs`: coi như hết hạn sớm hơn một chút, tránh gửi token còn 1 giây rồi tới server thì đã hết.
   */
  accessTokenExpired(skewMs = 10_000): boolean {
    const session = this.session();
    return session !== null && session.expiresAt - skewMs <= Date.now();
  }

  setSession(response: LoginResponse): void {
    const session: Session = {
      token: response.accessToken,
      expiresAt: Date.now() + response.expiresIn * 1000,
      user: response.user,
    };
    this.session.set(session);
    writeSession(session);
  }

  /** Cập nhật thông tin người dùng (sau khi sửa hồ sơ), GIỮ NGUYÊN token và hạn của phiên. */
  updateUser(user: User): void {
    const session = this.session();
    if (!session) {
      return;
    }
    const next: Session = { ...session, user };
    this.session.set(next);
    writeSession(next);
  }

  clear(): void {
    this.session.set(null);
    writeSession(null);
  }
}

function readSession(): Session | null {
  try {
    const raw = localStorage.getItem(STORAGE_KEY);
    if (!raw) {
      return null;
    }
    // [SỬA] Không bỏ phiên có access token hết hạn: refresh token (cookie) có thể vẫn còn hạn.
    return JSON.parse(raw) as Session;
  } catch {
    return null;
  }
}

function writeSession(session: Session | null): void {
  try {
    if (session) {
      localStorage.setItem(STORAGE_KEY, JSON.stringify(session));
    } else {
      localStorage.removeItem(STORAGE_KEY);
    }
  } catch {
    // Không lưu được (chế độ ẩn danh...): phiên vẫn dùng được tới khi tải lại trang.
  }
}
