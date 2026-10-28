import { HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { Router } from '@angular/router';
import { catchError, throwError } from 'rxjs';

import { APP_ENV } from '../config/app-env';
import { ApiError, ErrorResponse } from '../models/common.models';
import { AuthStore } from './auth.store';

/** Gắn "Authorization: Bearer <token>" vào mọi request gửi tới API của Cassin. */
export const authInterceptor: HttpInterceptorFn = (req, next) => {
  const token = inject(AuthStore).token();
  if (!token || !req.url.startsWith(APP_ENV.apiBaseUrl)) {
    return next(req);
  }
  return next(req.clone({ setHeaders: { Authorization: `Bearer ${token}` } }));
};

/**
 * Đổi mọi lỗi HTTP thành ApiError (cùng kiểu với mock), và xử lý hết phiên:
 * API trả 401 (token hết hạn / sai) → xóa phiên, về trang đăng nhập kèm returnUrl và lý do.
 * Không áp dụng cho chính API đăng nhập: ở đó 401 nghĩa là sai mật khẩu, form tự hiện lỗi.
 */
export const errorInterceptor: HttpInterceptorFn = (req, next) => {
  const store = inject(AuthStore);
  const router = inject(Router);

  return next(req).pipe(
    catchError((error: unknown) => {
      if (!(error instanceof HttpErrorResponse)) {
        return throwError(() => error);
      }
      if (error.status === 0) {
        return throwError(() => ApiError.network());
      }

      const body = error.error as Partial<ErrorResponse> | null;
      const apiError = new ApiError(
        error.status,
        body?.code ?? 'HTTP_' + error.status,
        body?.message ?? 'Đã có lỗi xảy ra. Vui lòng thử lại.',
        body?.fieldErrors ?? {},
      );

      const isAuthCall = req.url.includes('/auth/');
      if (error.status === 401 && !isAuthCall) {
        store.clear();
        void router.navigate(['/login'], { queryParams: { returnUrl: router.url, reason: 'expired' } });
      }
      return throwError(() => apiError);
    }),
  );
};
