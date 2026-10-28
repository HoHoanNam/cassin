import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { APP_ENV } from '../../config/app-env';
import { LoginRequest, LoginResponse, RegisterRequest, User } from '../../models/user.models';
import { AuthApi } from '../auth.api';

/** Bản gọi backend thật. Lỗi HTTP được errorInterceptor đổi thành ApiError. */
@Injectable()
export class HttpAuthApi extends AuthApi {
  private readonly http = inject(HttpClient);
  private readonly base = APP_ENV.apiBaseUrl;

  register(request: RegisterRequest): Observable<User> {
    return this.http.post<User>(`${this.base}/auth/register`, request);
  }

  login(request: LoginRequest): Observable<LoginResponse> {
    return this.http.post<LoginResponse>(`${this.base}/auth/login`, request);
  }

  me(): Observable<User> {
    return this.http.get<User>(`${this.base}/me`);
  }
}
