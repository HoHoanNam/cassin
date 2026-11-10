import { Observable } from 'rxjs';

import { ScanResult } from '../models/scan.models';

/**
 * Quét đoạn văn (chỉ ĐỌC). Thêm từ vào bộ dùng API có sẵn:
 *   LIBRARY → DeckApi.addWord(deckId, wordId)      POST /api/decks/{deckId}/words/{wordId}
 *   NEW     → WordApi.createWord({..., deckIds})    POST /api/words
 */
export abstract class ScanApi {
  /** POST /api/scan { text } */
  abstract scan(text: string): Observable<ScanResult>;
}
