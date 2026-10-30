import { Injectable, computed, signal } from '@angular/core';

import { LoginResponse, User } from '../models/user.models';

/** Phiên đăng nhập lưu trong trình duyệt. */
interface Session {
  token: string;
  /** Thời điểm hết hạn (ms, Date.now()) */
  expiresAt: number;
  user: User;
}

const STORAGE_KEY = 'cassin.session';

/**
 * Nơi DUY NHẤT giữ trạng thái đăng nhập của cả app (token + người dùng), dạng signal.
 *
 * Token lưu ở localStorage để tải lại trang không bị đăng xuất.
 * Lưu ý bảo mật: localStorage đọc được bằng JavaScript, nên nếu trang bị chèn script lạ (XSS)
 * thì token có thể bị lấy. Bản thương mại lâu dài nên cân nhắc chuyển sang cookie HttpOnly
 * (cần backend hỗ trợ). Với access token ngắn hạn (1 giờ) thì đây là đánh đổi chấp nhận được.
 *
 * Mọi thao tác với localStorage đều bọc try/catch: trình duyệt ẩn danh hoặc chặn lưu trữ sẽ ném lỗi.
 */
@Injectable({ providedIn: 'root' })
export class AuthStore {
  private readonly session = signal<Session | null>(readSession());

  readonly user = computed(() => this.session()?.user ?? null);
  readonly token = computed(() => this.session()?.token ?? null);
  readonly isAdmin = computed(() => this.user()?.role === 'ADMIN');

  /** Có phiên đăng nhập CÒN HẠN không. Gọi như hàm (không phải signal) vì phụ thuộc thời gian. */
  hasValidSession(): boolean {
    const session = this.session();
    return session !== null && session.expiresAt > Date.now();
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
    const session = JSON.parse(raw) as Session;
    return session.expiresAt > Date.now() ? session : null;
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
