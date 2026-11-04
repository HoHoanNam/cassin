import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { DeckApi } from '../api/deck.api';
import { PageResponse } from '../models/common.models';
import { Deck, DeckRequest, WordSummary } from '../models/deck.models';
import { DbDeck, MockDb } from './mock-db';
import { MockSession } from './mock-session';
import { simulate } from './mock-utils';
import { MockVocab, pageOf, validateDeck } from './mock-vocab';

/**
 * Giả lập DeckController. Luật quyền giống DeckService ở backend:
 *   - Bộ hệ thống: ai cũng xem được, không ai sửa được (403 DECK_READ_ONLY)
 *   - Bộ của người khác: 404 (giả vờ không tồn tại)
 * [V10] Bộ chứa TỪ qua bảng deck_words. Xóa bộ không xóa từ.
 */
@Injectable()
export class MockDeckApi extends DeckApi {
  private readonly db = inject(MockDb);
  private readonly vocab = inject(MockVocab);
  private readonly session = inject(MockSession);

  listDecks(): Observable<Deck[]> {
    return simulate(() => {
      const userId = this.session.userId();
      return this.db.decks
        .filter((d) => d.deckType === 'SYSTEM_DECK' || d.ownerId === userId)
        .sort((a, b) => a.deckType.localeCompare(b.deckType) || b.createdAt.localeCompare(a.createdAt))
        .map((d) => this.vocab.toDeck(d, userId));
    });
  }

  getDeck(deckId: number): Observable<Deck> {
    return simulate(() => {
      const userId = this.session.userId();
      return this.vocab.toDeck(this.vocab.readableDeck(deckId, userId), userId);
    });
  }

  createDeck(request: DeckRequest): Observable<Deck> {
    return simulate(() => {
      const userId = this.session.userId();
      validateDeck(request);
      const now = new Date().toISOString();
      const deck: DbDeck = {
        id: this.db.nextId(),
        deckName: request.deckName.trim(),
        description: request.description?.trim() || null,
        deckType: 'USER_DECK',
        ownerId: userId,
        createdAt: now,
        updatedAt: now,
      };
      this.db.decks.push(deck);
      this.db.save();
      return this.vocab.toDeck(deck, userId);
    });
  }

  updateDeck(deckId: number, request: DeckRequest): Observable<Deck> {
    return simulate(() => {
      const userId = this.session.userId();
      const deck = this.vocab.editableDeck(deckId, userId);
      validateDeck(request);
      deck.deckName = request.deckName.trim();
      deck.description = request.description?.trim() || null;
      deck.updatedAt = new Date().toISOString();
      this.db.save();
      return this.vocab.toDeck(deck, userId);
    });
  }

  deleteDeck(deckId: number): Observable<void> {
    return simulate(() => {
      const deck = this.vocab.editableDeck(deckId, this.session.userId());
      // [V10] Chỉ xóa bộ + dòng deck_words. Từ vẫn còn trong sổ từ.
      this.db.removeWhere('deckWords', (dw) => dw.deckId === deck.id);
      this.db.removeWhere('decks', (d) => d.id === deck.id);
      this.db.save();
    });
  }

  listWords(deckId: number, q: string, page: number, size: number): Observable<PageResponse<WordSummary>> {
    return simulate(() => {
      const userId = this.session.userId();
      this.vocab.readableDeck(deckId, userId);
      const ids = this.vocab.wordIdsIn(deckId);
      // findInDeck: order by w.term, w.id
      const words = this.db.words
        .filter((w) => ids.has(w.id))
        .sort((a, b) => a.term.localeCompare(b.term) || a.id - b.id);
      const result = pageOf(words, q, page, size);
      return { ...result, content: result.content.map((w) => this.vocab.toSummary(w, userId)) };
    });
  }

  addWord(deckId: number, wordId: number): Observable<void> {
    return simulate(() => {
      const userId = this.session.userId();
      const deck = this.vocab.editableDeck(deckId, userId);
      this.vocab.linkWord(deck, this.vocab.readableWord(wordId, userId));
      this.db.save();
    });
  }

  removeWord(deckId: number, wordId: number): Observable<void> {
    return simulate(() => {
      this.vocab.unlinkWord(this.vocab.editableDeck(deckId, this.session.userId()), wordId);
      this.db.save();
    });
  }
}
