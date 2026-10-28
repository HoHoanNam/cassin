import { Observable } from 'rxjs';

import { PageResponse } from '../models/common.models';
import { Card, CardRequest, Deck, DeckRequest } from '../models/deck.models';

/** Bộ từ và thẻ. Xem giải thích kiểu "hợp đồng" trong auth.api.ts. */
export abstract class DeckApi {
  /** GET /api/decks */
  abstract listDecks(): Observable<Deck[]>;
  /** GET /api/decks/{deckId} */
  abstract getDeck(deckId: number): Observable<Deck>;
  /** POST /api/decks → 201 */
  abstract createDeck(request: DeckRequest): Observable<Deck>;
  /** PUT /api/decks/{deckId} */
  abstract updateDeck(deckId: number, request: DeckRequest): Observable<Deck>;
  /** DELETE /api/decks/{deckId} → 204 */
  abstract deleteDeck(deckId: number): Observable<void>;

  /** GET /api/decks/{deckId}/cards?page=&size= */
  abstract listCards(deckId: number, page: number, size: number): Observable<PageResponse<Card>>;
  /** POST /api/decks/{deckId}/cards → 201 */
  abstract createCard(deckId: number, request: CardRequest): Observable<Card>;
  /** PUT /api/cards/{cardId} */
  abstract updateCard(cardId: number, request: CardRequest): Observable<Card>;
  /** DELETE /api/cards/{cardId} → 204 */
  abstract deleteCard(cardId: number): Observable<void>;
}
