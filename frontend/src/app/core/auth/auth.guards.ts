import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';

import { AuthStore } from './auth.store';

/**
 * Trang cần đăng nhập. Chưa đăng nhập → về /login, kèm returnUrl để đăng nhập xong quay lại đúng trang.
 * Lưu ý: guard chỉ là trải nghiệm người dùng. Bảo vệ thật nằm ở backend (API trả 401 khi thiếu token).
 */
export const authGuard: CanActivateFn = (_route, state) => {
  const store = inject(AuthStore);
  if (store.hasValidSession()) {
    return true;
  }
  return inject(Router).createUrlTree(['/login'], { queryParams: { returnUrl: state.url } });
};

/** Trang dành cho khách (landing, đăng nhập, đăng ký). Đã đăng nhập → vào thẳng trang Hôm nay. */
export const guestGuard: CanActivateFn = () => {
  const store = inject(AuthStore);
  return store.hasValidSession() ? inject(Router).createUrlTree(['/today']) : true;
};
