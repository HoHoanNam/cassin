import { Observable } from 'rxjs';

import { ReviewCard, ReviewResult, ReviewStats } from '../models/review.models';

/** Ôn thẻ theo hộp Leitner. */
export abstract class ReviewApi {
  /** GET /api/decks/{deckId}/review-queue?newLimit= */
  abstract deckQueue(deckId: number, newLimit: number): Observable<ReviewCard[]>;
  /**
   * [CẦN BE BỔ SUNG] GET /api/review-queue?newLimit=
   * Hàng đợi gộp MỌI bộ từ (nút "Bắt đầu ôn 18 thẻ" ở trang Hôm nay).
   * Backend hiện chỉ có hàng đợi theo từng bộ.
   */
  abstract allQueue(newLimit: number): Observable<ReviewCard[]>;
  /** POST /api/cards/{cardId}/review */
  abstract review(cardId: number, remembered: boolean): Observable<ReviewResult>;
  /** GET /api/review/stats */
  abstract stats(): Observable<ReviewStats>;
}
