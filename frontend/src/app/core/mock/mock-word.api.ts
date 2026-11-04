import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { WordApi } from '../api/word.api';
import { PageResponse } from '../models/common.models';
import { Word, WordRequest, WordSummary } from '../models/deck.models';
import { DbWord, MockDb } from './mock-db';
import { MockSession } from './mock-session';
import { conflict, simulate } from './mock-utils';
import { MockVocab, pageOf } from './mock-vocab';

/** [V10] Giả lập WordController + WordService (sổ từ, tạo / sửa / xóa từ nhiều nghĩa). */
@Injectable()
export class MockWordApi extends WordApi {
  private readonly db = inject(MockDb);
  private readonly vocab = inject(MockVocab);
  private readonly session = inject(MockSession);

  listWords(
    q: string,
    page: number,
    size: number,
    deckId?: number,
  ): Observable<PageResponse<WordSummary>> {
    return simulate(() => {
      const userId = this.session.userId();
      let words: DbWord[];
      if (deckId !== undefined) {
        this.vocab.readableDeck(deckId, userId);
        const ids = this.vocab.wordIdsIn(deckId);
        words = this.db.words
          .filter((w) => ids.has(w.id))
          .sort((a, b) => a.term.localeCompare(b.term) || a.id - b.id);
      } else {
        // findNotebook: order by w.createdAt desc, w.id desc
        words = this.vocab
          .notebookWords(userId)
          .sort((a, b) => b.createdAt.localeCompare(a.createdAt) || b.id - a.id);
      }
      const result = pageOf(words, q, page, size);
      return { ...result, content: result.content.map((w) => this.vocab.toSummary(w, userId)) };
    });
  }

  getWord(wordId: number): Observable<Word> {
    return simulate(() => {
      const userId = this.session.userId();
      return this.vocab.toWord(this.vocab.readableWord(wordId, userId), userId);
    });
  }

  createWord(request: WordRequest): Observable<Word> {
    return simulate(() => {
      const userId = this.session.userId();
      if (this.vocab.termExists(userId, request.term ?? '')) {
        throw wordExists(request.term);
      }
      this.vocab.validateWord(request, null);
      // Kiểm tra quyền mọi bộ TRƯỚC khi ghi (backend chạy trong một transaction, lỗi thì không lưu gì).
      const decks = [...new Set(request.deckIds ?? [])].map((id) =>
        this.vocab.editableDeck(id, userId),
      );
      const word = this.vocab.createWord(userId, request);
      decks.forEach((deck) => this.vocab.linkWord(deck, word));
      this.db.save();
      return this.vocab.toWord(word, userId);
    });
  }

  updateWord(wordId: number, request: WordRequest): Observable<Word> {
    return simulate(() => {
      const userId = this.session.userId();
      const word = this.vocab.editableWord(wordId, userId);
      if (this.vocab.termExists(userId, request.term ?? '', word.id)) {
        throw wordExists(request.term);
      }
      this.vocab.validateWord(request, word);
      const wanted = request.deckIds === null ? null : new Set(request.deckIds);
      const wantedDecks =
        wanted === null ? [] : [...wanted].map((id) => this.vocab.editableDeck(id, userId));
      this.vocab.replaceSenses(word, request);
      if (wanted !== null) {
        // syncOwnDecks: bỏ khỏi bộ của mình không còn trong danh sách, thêm vào bộ mới
        this.db.decks
          .filter((d) => d.ownerId === userId && !wanted.has(d.id))
          .forEach((d) => this.vocab.unlinkWord(d, word.id));
        wantedDecks.forEach((d) => this.vocab.linkWord(d, word));
      }
      this.db.save();
      return this.vocab.toWord(word, userId);
    });
  }

  deleteWord(wordId: number): Observable<void> {
    return simulate(() => {
      this.vocab.deleteWord(this.vocab.editableWord(wordId, this.session.userId()));
      this.db.save();
    });
  }
}

function wordExists(term: string) {
  return conflict(
    'WORD_EXISTS',
    `Từ "${term.trim()}" đã có trong sổ từ. Hãy thêm nghĩa mới vào từ đó.`,
  );
}
