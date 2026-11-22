import { Observable } from 'rxjs';

import { Badge, EarnedBadge, SeenBadge } from '../models/badge.models';

/**
 * [THÊM] Huy hiệu. Module com.cassin.badge ở backend (chưa có): xem docs thiết kế "Badge".
 *
 * Backend CẤP huy hiệu bằng sự kiện nội bộ (ReviewAnsweredEvent, QuizSubmittedEvent...) sau khi
 * transaction của hành động học tập commit. Frontend KHÔNG gọi API "cấp huy hiệu", chỉ:
 *   1. xem toàn bộ huy hiệu + tiến độ,
 *   2. hỏi "có huy hiệu nào vừa mở khóa mà mình chưa xem không?" để hiện popup,
 *   3. báo đã xem để popup không hiện lại.
 */
export abstract class BadgeApi {
  /** GET /api/badges → mọi huy hiệu kèm tiến độ của người đang đăng nhập (bí mật chưa đạt bị che) */
  abstract list(): Observable<Badge[]>;
  /** GET /api/badges/unseen → các CẤP đã đạt nhưng chưa xem popup, cũ nhất trước */
  abstract unseen(): Observable<EarnedBadge[]>;
  /** POST /api/badges/seen { items: [{code, level}] } → 204. Cặp chưa đạt / không tồn tại bị bỏ qua (idempotent). */
  abstract markSeen(items: SeenBadge[]): Observable<void>;
}
