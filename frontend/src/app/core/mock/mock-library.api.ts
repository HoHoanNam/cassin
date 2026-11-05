import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { LibraryApi } from '../api/library.api';
import { PageResponse } from '../models/common.models';
import { LibraryWord } from '../models/deck.models';
import { MockDb } from './mock-db';
import { MockSession } from './mock-session';
import { simulate } from './mock-utils';
import { MockVocab, pageOf } from './mock-vocab';

/** Giả lập LibraryService: từ hệ thống A → Z, kèm 2 cờ inMyDecks / studying của người đang xem. */
@Injectable()
export class MockLibraryApi extends LibraryApi {
  private readonly db = inject(MockDb);
  private readonly vocab = inject(MockVocab);
  private readonly session = inject(MockSession);

  search(q: string, onlyNew: boolean, page: number, size: number): Observable<PageResponse<LibraryWord>> {
    return simulate(() => {
      const userId = this.session.userId();
      // findInOwnDecks: từ nằm trong bộ CỦA TÔI
      const myDeckIds = new Set(this.db.decks.filter((d) => d.ownerId === userId).map((d) => d.id));
      const inMyDecks = new Set(this.db.deckWords.filter((dw) => myDeckIds.has(dw.deckId)).map((dw) => dw.wordId));
      // findStudiedWords: từ tôi đã học ít nhất một nghĩa
      const studiedSenses = new Set(this.db.progresses.filter((p) => p.userId === userId).map((p) => p.senseId));
      const studying = new Set(this.db.senses.filter((s) => studiedSenses.has(s.id)).map((s) => s.wordId));

      const words = this.db.words
        .filter((w) => w.ownerId === null)
        .filter((w) => !onlyNew || (!inMyDecks.has(w.id) && !studying.has(w.id)))
        .sort((a, b) => a.term.localeCompare(b.term) || a.id - b.id);
      const result = pageOf(words, q, page, size);
      return {
        ...result,
        content: result.content.map((w) => {
          const { editable: _editable, system: _system, ...summary } = this.vocab.toSummary(w, userId);
          return { ...summary, inMyDecks: inMyDecks.has(w.id), studying: studying.has(w.id) };
        }),
      };
    });
  }
}
