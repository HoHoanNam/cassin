import { Injectable, inject } from '@angular/core';

import { AuthStore } from '../auth/auth.store';
import { ApiError } from '../models/common.models';

/**
 * Mock không có header Authorization: lấy "người đang gọi API" từ AuthStore.
 * Tương đương @AuthenticationPrincipal AuthUser ở backend.
 */
@Injectable({ providedIn: 'root' })
export class MockSession {
  private readonly store = inject(AuthStore);

  userId(): number {
    const user = this.store.user();
    if (!user || !this.store.hasValidSession()) {
      throw new ApiError(401, 'UNAUTHORIZED', 'Bạn cần đăng nhập');
    }
    return user.id;
  }
}
