import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { AuthApi } from '../api/auth.api';
import { ApiError } from '../models/common.models';
import { LoginRequest, LoginResponse, RegisterRequest, User } from '../models/user.models';
import { MockDb, DbUser } from './mock-db';
import { MockSession } from './mock-session';
import { conflict, isBlank, simulate, tooLong, validate } from './mock-utils';

const TOKEN_TTL_SECONDS = 60 * 60;

/** Giả lập AuthController + AuthService của backend, cùng luật và cùng mã lỗi. */
@Injectable()
export class MockAuthApi extends AuthApi {
  private readonly db = inject(MockDb);
  private readonly session = inject(MockSession);

  register(request: RegisterRequest): Observable<User> {
    return simulate(() => {
      validate([
        ['email', isBlank(request.email), 'Vui lòng nhập email'],
        ['email', !/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(request.email ?? ''), 'Email không hợp lệ'],
        ['password', (request.password ?? '').length < 8, 'Mật khẩu tối thiểu 8 ký tự'],
        ['displayName', isBlank(request.displayName), 'Vui lòng nhập tên hiển thị'],
        ['displayName', tooLong(request.displayName, 100), 'Tên hiển thị tối đa 100 ký tự'],
      ]);
      const email = normalizeEmail(request.email);
      if (this.db.users.some((u) => u.email === email)) {
        throw conflict('EMAIL_TAKEN', 'Email này đã được đăng ký');
      }
      const user: DbUser = {
        id: this.db.nextId(),
        email,
        password: request.password,
        displayName: request.displayName.trim(),
        role: 'USER',
        bio: null,
      };
      this.db.users.push(user);
      this.db.save();
      return toUser(user);
    });
  }

  login(request: LoginRequest): Observable<LoginResponse> {
    return simulate(() => {
      validate([
        ['email', isBlank(request.email), 'Vui lòng nhập email'],
        ['password', isBlank(request.password), 'Vui lòng nhập mật khẩu'],
      ]);
      const email = normalizeEmail(request.email);
      const user = this.db.users.find((u) => u.email === email && u.password === request.password);
      // Sai email hay sai mật khẩu đều CÙNG một lỗi, giống backend.
      if (!user) {
        throw new ApiError(401, 'INVALID_CREDENTIALS', 'Email hoặc mật khẩu không đúng');
      }
      return {
        accessToken: `mock.${user.id}.${Date.now()}`,
        tokenType: 'Bearer',
        expiresIn: TOKEN_TTL_SECONDS,
        user: toUser(user),
      };
    });
  }

  me(): Observable<User> {
    return simulate(() => {
      const user = this.db.users.find((u) => u.id === this.session.userId());
      if (!user) {
        throw new ApiError(404, 'USER_NOT_FOUND', 'Không tìm thấy tài khoản');
      }
      return toUser(user);
    });
  }
}

export function normalizeEmail(email: string): string {
  return (email ?? '').trim().toLowerCase();
}

export function toUser(user: DbUser): User {
  return { id: user.id, email: user.email, displayName: user.displayName, role: user.role, bio: user.bio };
}
