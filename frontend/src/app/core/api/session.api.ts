import { Observable } from 'rxjs';

import { RevokedSessions, Session } from '../models/session.models';

/**
 * [THÊM] Thiết bị đang đăng nhập (trang Tài khoản).
 * Nằm dưới /api/auth để trình duyệt gửi kèm cookie refresh (Path=/api/auth) → server biết "thiết bị này".
 */
export abstract class SessionApi {
  /** GET /api/auth/sessions: mới hoạt động nhất lên đầu */
  abstract list(): Observable<Session[]>;
  /** DELETE /api/auth/sessions/{id} → 204 | 404 SESSION_NOT_FOUND */
  abstract revoke(id: string): Observable<void>;
  /** DELETE /api/auth/sessions → { revoked } (giữ thiết bị hiện tại) */
  abstract revokeOthers(): Observable<RevokedSessions>;
}
