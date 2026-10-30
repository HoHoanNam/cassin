import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { APP_ENV } from '../../config/app-env';
import { ChangePasswordRequest, UpdateProfileRequest, User } from '../../models/user.models';
import { AccountApi } from '../account.api';

@Injectable()
export class HttpAccountApi extends AccountApi {
  private readonly http = inject(HttpClient);
  private readonly base = APP_ENV.apiBaseUrl;

  updateProfile(request: UpdateProfileRequest): Observable<User> {
    return this.http.put<User>(`${this.base}/me`, request);
  }

  changePassword(request: ChangePasswordRequest): Observable<void> {
    return this.http.put<void>(`${this.base}/me/password`, request);
  }
}
