import { Observable } from 'rxjs';

import { PageResponse } from '../models/common.models';
import { Word, WordRequest, WordSummary } from '../models/deck.models';

/**
 * [V10] Sổ từ: từ của mình + từ hệ thống mình đang học / đã đặt vào bộ của mình.
 * Từ hệ thống đọc được nhưng không sửa được (403 WORD_READ_ONLY).
 */
export abstract class WordApi {
  /** GET /api/words?q=&page=&size= (cả sổ từ) hoặc ?deckId= (từ trong một bộ) */
  abstract listWords(
    q: string,
    page: number,
    size: number,
    deckId?: number,
  ): Observable<PageResponse<WordSummary>>;
  /** GET /api/words/{wordId} */
  abstract getWord(wordId: number): Observable<Word>;
  /** POST /api/words → 201 | 409 WORD_EXISTS (đã có từ cùng cách viết trong sổ từ) */
  abstract createWord(request: WordRequest): Observable<Word>;
  /** PUT /api/words/{wordId} → 403 WORD_READ_ONLY nếu là từ hệ thống */
  abstract updateWord(wordId: number, request: WordRequest): Observable<Word>;
  /** DELETE /api/words/{wordId} → 204. Xóa luôn tiến độ ôn các nghĩa của từ. */
  abstract deleteWord(wordId: number): Observable<void>;
}
