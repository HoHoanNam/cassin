import { Observable } from 'rxjs';

import { ReviewCard, ReviewResult, ReviewStats } from '../models/review.models';

/** Ôn thẻ theo hộp Leitner. [V10] Mỗi thẻ là MỘT nghĩa của một từ. */
export abstract class ReviewApi {
  /**
   * GET /api/review-queue?deckId=&newLimit=
   * deckId = null: ôn cả sổ từ. Thẻ đến hạn trước, rồi tối đa newLimit thẻ mới.
   */
  abstract queue(deckId: number | null, newLimit: number): Observable<ReviewCard[]>;
  /** POST /api/senses/{senseId}/review → 409 REVIEW_NOT_ALLOWED nếu đã ôn hôm nay / chưa đến hạn */
  abstract review(senseId: number, remembered: boolean): Observable<ReviewResult>;
  /** GET /api/review/stats */
  abstract stats(): Observable<ReviewStats>;
}
