import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { AccountApi } from '../api/account.api';
import { ApiError } from '../models/common.models';
import { ChangePasswordRequest, PROFILE_LIMITS, UpdateProfileRequest, User } from '../models/user.models';
import { toUser } from './mock-auth.api';
import { DbUser, MockDb } from './mock-db';
import { MockSession } from './mock-session';
import { businessError, isBlank, simulate, tooLong, validate } from './mock-utils';

/** Giả lập PUT /api/me và PUT /api/me/password (UserService ở backend). */
@Injectable()
export class MockAccountApi extends AccountApi {
  private readonly db = inject(MockDb);
  private readonly session = inject(MockSession);

  updateProfile(request: UpdateProfileRequest): Observable<User> {
    return simulate(() => {
      const user = this.currentUser();
      validate([
        ['displayName', isBlank(request.displayName), 'Vui lòng nhập tên hiển thị'],
        ['displayName', tooLong(request.displayName, PROFILE_LIMITS.displayName), 'Tên hiển thị tối đa 100 ký tự'],
        ['bio', tooLong(request.bio, PROFILE_LIMITS.bio), 'Giới thiệu tối đa 500 ký tự'],
      ]);
      user.displayName = request.displayName.trim();
      user.bio = request.bio?.trim() || null;
      this.db.save();
      return toUser(user);
    });
  }

  changePassword(request: ChangePasswordRequest): Observable<void> {
    return simulate(() => {
      const user = this.currentUser();
      const length = (request.newPassword ?? '').length;
      validate([
        ['currentPassword', isBlank(request.currentPassword), 'Vui lòng nhập mật khẩu hiện tại'],
        [
          'newPassword',
          length < PROFILE_LIMITS.passwordMin || length > PROFILE_LIMITS.passwordMax,
          'Mật khẩu từ 8 đến 72 ký tự',
        ],
      ]);
      if (request.currentPassword !== user.password) {
        throw businessError('WRONG_PASSWORD', 'Mật khẩu hiện tại không đúng');
      }
      // Giống PasswordRules ở backend: BCrypt giới hạn 72 BYTE (chữ có dấu tốn 2–3 byte), không phải 72 ký tự.
      if (new TextEncoder().encode(request.newPassword).length > PROFILE_LIMITS.passwordMax) {
        throw businessError('PASSWORD_TOO_LONG', 'Mật khẩu quá dài. Chữ có dấu tốn nhiều chỗ hơn chữ không dấu, hãy rút ngắn hoặc bớt dấu.');
      }
      if (request.newPassword === user.password) {
        throw businessError('SAME_PASSWORD', 'Mật khẩu mới phải khác mật khẩu cũ');
      }
      user.password = request.newPassword;
      this.db.save();
    });
  }

  private currentUser(): DbUser {
    const user = this.db.users.find((u) => u.id === this.session.userId());
    if (!user) {
      throw new ApiError(404, 'USER_NOT_FOUND', 'Không tìm thấy tài khoản');
    }
    return user;
  }
}
