import { Observable } from 'rxjs';

import { ChangePasswordRequest, UpdateProfileRequest, User } from '../models/user.models';

/** Tài khoản của người đang đăng nhập (đọc thông tin: AuthApi.me()). */
export abstract class AccountApi {
  /** PUT /api/me → UserResponse mới */
  abstract updateProfile(request: UpdateProfileRequest): Observable<User>;
  /** PUT /api/me/password → 204 | 400 WRONG_PASSWORD | 400 SAME_PASSWORD */
  abstract changePassword(request: ChangePasswordRequest): Observable<void>;
}
