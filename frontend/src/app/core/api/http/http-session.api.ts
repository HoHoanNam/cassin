import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { APP_ENV } from '../../config/app-env';
import { RevokedSessions, Session } from '../../models/session.models';
import { SessionApi } from '../session.api';

/** [THÊM] Cookie refresh tự đi kèm (cùng origin, Path=/api/auth); Bearer do authInterceptor gắn. */
@Injectable()
export class HttpSessionApi extends SessionApi {
  private readonly http = inject(HttpClient);
  private readonly base = `${APP_ENV.apiBaseUrl}/auth/sessions`;

  list(): Observable<Session[]> {
    return this.http.get<Session[]>(this.base);
  }

  revoke(id: string): Observable<void> {
    return this.http.delete<void>(`${this.base}/${encodeURIComponent(id)}`);
  }

  revokeOthers(): Observable<RevokedSessions> {
    return this.http.delete<RevokedSessions>(this.base);
  }
}
