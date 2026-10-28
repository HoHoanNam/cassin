import { Injectable, inject } from '@angular/core';
import { Router } from '@angular/router';
import { Observable, switchMap, tap } from 'rxjs';

import { AuthApi } from '../api/auth.api';
import { LoginRequest, RegisterRequest, User } from '../models/user.models';
import { AuthStore } from './auth.store';

/** Các thao tác đăng nhập / đăng ký / đăng xuất. Component gọi service này, không gọi AuthApi trực tiếp. */
@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly api = inject(AuthApi);
  private readonly store = inject(AuthStore);
  private readonly router = inject(Router);

  login(request: LoginRequest): Observable<User> {
    return this.api.login(request).pipe(
      tap((response) => this.store.setSession(response)),
      switchMap((response) => [response.user]),
    );
  }

  /**
   * Đăng ký rồi ĐĂNG NHẬP LUÔN: backend trả 201 + thông tin user (không có token),
   * nên gọi tiếp login bằng chính email + mật khẩu vừa nhập. Người dùng không phải gõ lại.
   */
  register(request: RegisterRequest): Observable<User> {
    return this.api
      .register(request)
      .pipe(switchMap(() => this.login({ email: request.email, password: request.password })));
  }

  logout(): void {
    this.store.clear();
    void this.router.navigateByUrl('/');
  }
}
