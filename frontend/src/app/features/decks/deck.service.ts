import { Injectable } from '@angular/core';
import { Observable, delay, of } from 'rxjs';

import { DECK_MOCK } from './deck.mock';
import { DeckDetail } from './deck.models';

@Injectable({ providedIn: 'root' })
export class DeckService {
  /**
   * Chi tiết một bộ và các thẻ của nó. Trả null nếu không có bộ này.
   * API thật: GET /api/decks/{id} và GET /api/decks/{id}/cards (có phân trang).
   */
  getDeck(id: number): Observable<DeckDetail | null> {
    return of(DECK_MOCK[id] ?? null).pipe(delay(250));
  }
}
