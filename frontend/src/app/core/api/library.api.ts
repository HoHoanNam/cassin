import { Observable } from 'rxjs';

import { PageResponse } from '../models/common.models';
import { LibraryWord } from '../models/deck.models';

/**
 * Thư viện từ hệ thống. Đặt một từ vào bộ của mình dùng DeckApi.addWord (API có sẵn).
 */
export abstract class LibraryApi {
  /** GET /api/library?q=&onlyNew=&page=&size= . onlyNew = true: chỉ từ chưa có trong sổ từ. */
  abstract search(q: string, onlyNew: boolean, page: number, size: number): Observable<PageResponse<LibraryWord>>;
}
