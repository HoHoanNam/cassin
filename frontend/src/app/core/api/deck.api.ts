import { Observable } from 'rxjs';

import { PageResponse } from '../models/common.models';
import { Deck, DeckRequest, WordSummary } from '../models/deck.models';
import { ImportResult } from '../models/import.models';

/**
 * Bộ từ. Xem giải thích kiểu "hợp đồng" trong auth.api.ts.
 * [V10] Bộ là NHÃN chủ đề chứa từ (nhiều-nhiều): một từ nằm được trong nhiều bộ.
 * Tạo / sửa / xóa TỪ nằm ở WordApi.
 */
export abstract class DeckApi {
  /** GET /api/decks */
  abstract listDecks(): Observable<Deck[]>;
  /** GET /api/decks/{deckId} */
  abstract getDeck(deckId: number): Observable<Deck>;
  /** POST /api/decks → 201 */
  abstract createDeck(request: DeckRequest): Observable<Deck>;
  /** PUT /api/decks/{deckId} */
  abstract updateDeck(deckId: number, request: DeckRequest): Observable<Deck>;
  /** DELETE /api/decks/{deckId} → 204. Chỉ xóa bộ, từ vẫn còn trong sổ từ. */
  abstract deleteDeck(deckId: number): Observable<void>;

  /** GET /api/decks/{deckId}/words?q=&page=&size= */
  abstract listWords(deckId: number, q: string, page: number, size: number): Observable<PageResponse<WordSummary>>;
  /** POST /api/decks/{deckId}/words/{wordId} → 204. Đã có trong bộ thì không làm gì. */
  abstract addWord(deckId: number, wordId: number): Observable<void>;
  /** DELETE /api/decks/{deckId}/words/{wordId} → 204. Chỉ bỏ khỏi bộ, không xóa từ. */
  abstract removeWord(deckId: number, wordId: number): Observable<void>;

  /**
   * POST /api/decks/{deckId}/import?dryRun=   (multipart/form-data, part "file")
   * dryRun = true: chỉ kiểm tra + đếm, không lưu. Lỗi từng dòng vẫn trả 200 trong errors[].
   * Lỗi cả file (rỗng, thiếu cột, > 500 dòng) → 400; file > 1MB → 413.
   */
  abstract importWords(deckId: number, file: File, dryRun: boolean): Observable<ImportResult>;
}
