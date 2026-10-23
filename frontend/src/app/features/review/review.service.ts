import { Injectable } from '@angular/core';
import { Observable, delay, of } from 'rxjs';

import { REVIEW_MOCK } from './review.mock';
import { ReviewCard } from './review.models';

@Injectable({ providedIn: 'root' })
export class ReviewService {
  /**
   * Lấy các thẻ đến hạn. deckId = null: ôn tất cả các bộ.
   * API thật: GET /api/decks/{deckId}/review (hoặc /api/review khi ôn tất cả).
   */
  getSession(deckId: number | null): Observable<ReviewCard[]> {
    const cards = deckId === null ? REVIEW_MOCK : REVIEW_MOCK.slice(0, 4);
    return of(cards).pipe(delay(250));
  }

  /**
   * Gửi kết quả một thẻ, nhận về hộp mới.
   * API thật: POST /api/cards/{cardId}/review với body { "remembered": true }.
   *
   * Mock tự tính hộp mới theo đúng rule của CardProgress.review() ở backend.
   * Khi có API, server tính và trả về; giao diện không tự tính nữa.
   */
  answer(cardId: number, currentBox: number, remembered: boolean): Observable<{ toBox: number }> {
    const toBox = remembered ? Math.min(currentBox + 1, 5) : 1;
    return of({ toBox }).pipe(delay(120));
  }
}
