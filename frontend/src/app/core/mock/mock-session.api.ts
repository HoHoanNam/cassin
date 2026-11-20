import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { SessionApi } from '../api/session.api';
import { RevokedSessions, Session } from '../models/session.models';
import { MockDevices } from './mock-devices';
import { MockSession } from './mock-session';
import { notFound, simulate } from './mock-utils';

/** [THÊM] Giả lập GET / DELETE /api/auth/sessions (RefreshTokenService ở backend). */
@Injectable()
export class MockSessionApi extends SessionApi {
  private readonly devices = inject(MockDevices);
  private readonly session = inject(MockSession);

  list(): Observable<Session[]> {
    return simulate(() =>
      [...this.devices.of(this.session.userId())].sort((a, b) => b.lastActiveAt.localeCompare(a.lastActiveAt)),
    );
  }

  revoke(id: string): Observable<void> {
    return simulate(() => {
      const removed = this.devices.keep(this.session.userId(), (s) => s.id !== id);
      if (removed === 0) {
        throw notFound('SESSION_NOT_FOUND', 'Không tìm thấy phiên đăng nhập');
      }
    });
  }

  revokeOthers(): Observable<RevokedSessions> {
    return simulate(() => ({ revoked: this.devices.keep(this.session.userId(), (s) => s.current) }));
  }
}
