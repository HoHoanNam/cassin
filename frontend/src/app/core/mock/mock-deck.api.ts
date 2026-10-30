import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { DeckApi } from '../api/deck.api';
import { PageResponse } from '../models/common.models';
import { CARD_LIMITS, Card, CardRequest, DECK_LIMITS, Deck, DeckRequest } from '../models/deck.models';
import { todayIso } from '../util/dates';
import { DbCard, DbDeck, MockDb } from './mock-db';
import { MockSession } from './mock-session';
import { conflict, forbidden, isBlank, notFound, simulate, tooLong, validate } from './mock-utils';

/**
 * Giả lập DeckController + CardController. Luật quyền giống DeckService ở backend:
 *   - Bộ hệ thống: ai cũng xem được, không ai sửa được (403 DECK_READ_ONLY)
 *   - Bộ của người khác: 404 (giả vờ không tồn tại)
 */
@Injectable()
export class MockDeckApi extends DeckApi {
  private readonly db = inject(MockDb);
  private readonly session = inject(MockSession);

  listDecks(): Observable<Deck[]> {
    return simulate(() => {
      const userId = this.session.userId();
      return this.db.decks
        .filter((d) => d.deckType === 'SYSTEM_DECK' || d.ownerId === userId)
        .sort((a, b) => a.deckType.localeCompare(b.deckType) || b.createdAt.localeCompare(a.createdAt))
        .map((d) => this.toDeck(d, userId));
    });
  }

  getDeck(deckId: number): Observable<Deck> {
    return simulate(() => {
      const userId = this.session.userId();
      return this.toDeck(this.findReadable(deckId, userId), userId);
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
      return this.toDeck(deck, userId);
    });
  }

  updateDeck(deckId: number, request: DeckRequest): Observable<Deck> {
    return simulate(() => {
      const userId = this.session.userId();
      const deck = this.findEditable(deckId, userId);
      validateDeck(request);
      deck.deckName = request.deckName.trim();
      deck.description = request.description?.trim() || null;
      deck.updatedAt = new Date().toISOString();
      this.db.save();
      return this.toDeck(deck, userId);
    });
  }

  deleteDeck(deckId: number): Observable<void> {
    return simulate(() => {
      const deck = this.findEditable(deckId, this.session.userId());
      // ON DELETE CASCADE: xóa bộ → xóa thẻ → xóa tiến độ của các thẻ đó.
      const cardIds = new Set(this.db.cards.filter((c) => c.deckId === deck.id).map((c) => c.id));
      this.db.removeWhere('progresses', (p) => cardIds.has(p.cardId));
      this.db.removeWhere('cards', (c) => c.deckId === deck.id);
      this.db.removeWhere('decks', (d) => d.id === deck.id);
      this.db.save();
    });
  }

  listCards(deckId: number, page: number, size: number): Observable<PageResponse<Card>> {
    return simulate(() => {
      this.findReadable(deckId, this.session.userId());
      const safePage = Math.max(page, 0);
      const safeSize = Math.min(Math.max(size, 1), 100);
      const all = this.db.cards
        .filter((c) => c.deckId === deckId)
        .sort((a, b) => a.createdAt.localeCompare(b.createdAt) || a.id - b.id);
      return {
        content: all.slice(safePage * safeSize, (safePage + 1) * safeSize).map(toCard),
        page: safePage,
        size: safeSize,
        totalElements: all.length,
        totalPages: Math.ceil(all.length / safeSize),
      };
    });
  }

  createCard(deckId: number, request: CardRequest): Observable<Card> {
    return simulate(() => {
      const deck = this.findEditable(deckId, this.session.userId());
      validateCard(request);
      assertCardNotDuplicate(this.db, deck.id, request, null);
      const card: DbCard = {
        id: this.db.nextId(),
        deckId: deck.id,
        word: request.word.trim(),
        partOfSpeech: request.partOfSpeech,
        phonetic: request.phonetic?.trim() || null,
        definition: request.definition.trim(),
        examples: cleanExamples(request.examples),
        createdAt: new Date().toISOString(),
      };
      this.db.cards.push(card);
      deck.updatedAt = card.createdAt;
      this.db.save();
      return toCard(card);
    });
  }

  updateCard(cardId: number, request: CardRequest): Observable<Card> {
    return simulate(() => {
      const card = this.findEditableCard(cardId);
      validateCard(request);
      assertCardNotDuplicate(this.db, card.deckId, request, card.id);
      card.word = request.word.trim();
      card.partOfSpeech = request.partOfSpeech;
      card.phonetic = request.phonetic?.trim() || null;
      card.definition = request.definition.trim();
      card.examples = cleanExamples(request.examples);
      this.db.save();
      return toCard(card);
    });
  }

  deleteCard(cardId: number): Observable<void> {
    return simulate(() => {
      const card = this.findEditableCard(cardId);
      this.db.removeWhere('progresses', (p) => p.cardId === card.id);
      this.db.removeWhere('cards', (c) => c.id === card.id);
      this.db.save();
    });
  }

  // ===== Luật quyền (giống DeckService.checkReadable / checkEditable) =====

  private findReadable(deckId: number, userId: number): DbDeck {
    const deck = this.db.decks.find((d) => d.id === deckId);
    if (!deck || (deck.deckType !== 'SYSTEM_DECK' && deck.ownerId !== userId)) {
      throw notFound('DECK_NOT_FOUND', 'Không tìm thấy bộ từ');
    }
    return deck;
  }

  private findEditable(deckId: number, userId: number): DbDeck {
    const deck = this.findReadable(deckId, userId);
    if (deck.ownerId !== userId) {
      throw forbidden('DECK_READ_ONLY', 'Bộ từ hệ thống chỉ để ôn, không sửa được');
    }
    return deck;
  }

  private findEditableCard(cardId: number): DbCard {
    const card = this.db.cards.find((c) => c.id === cardId);
    if (!card) {
      throw notFound('CARD_NOT_FOUND', 'Không tìm thấy thẻ');
    }
    this.findEditable(card.deckId, this.session.userId());
    return card;
  }

  private toDeck(deck: DbDeck, userId: number): Deck {
    const today = todayIso();
    const cards = this.db.cards.filter((c) => c.deckId === deck.id);
    const progressByCard = new Map(
      this.db.progresses.filter((p) => p.userId === userId).map((p) => [p.cardId, p] as const),
    );
    let dueCount = 0;
    let newCount = 0;
    for (const card of cards) {
      const progress = progressByCard.get(card.id);
      if (!progress) {
        newCount++;
      } else if (progress.dueDate <= today) {
        dueCount++;
      }
    }
    return {
      id: deck.id,
      deckName: deck.deckName,
      description: deck.description,
      deckType: deck.deckType,
      cardCount: cards.length,
      editable: deck.ownerId === userId,
      dueCount,
      newCount,
      updatedAt: deck.updatedAt,
    };
  }
}

export function toCard(card: DbCard): Card {
  return {
    id: card.id,
    word: card.word,
    partOfSpeech: card.partOfSpeech,
    phonetic: card.phonetic,
    definition: card.definition,
    examples: [...card.examples],
  };
}

/** Giống unique index (deck_id, lower(word), lower(definition)) trong V3. Dùng chung với MockAdminApi. */
export function assertCardNotDuplicate(db: MockDb, deckId: number, request: CardRequest, exceptCardId: number | null): void {
  const word = request.word.trim().toLowerCase();
  const definition = request.definition.trim().toLowerCase();
  const duplicated = db.cards.some(
    (c) =>
      c.deckId === deckId &&
      c.id !== exceptCardId &&
      c.word.toLowerCase() === word &&
      c.definition.toLowerCase() === definition,
  );
  if (duplicated) {
    throw conflict('DATA_CONFLICT', 'Bộ này đã có thẻ cùng từ và cùng nghĩa');
  }
}

export function validateDeck(request: DeckRequest): void {
  validate([
    ['deckName', isBlank(request.deckName), 'Vui lòng nhập tên bộ'],
    ['deckName', tooLong(request.deckName, DECK_LIMITS.deckName), 'Tên bộ tối đa 100 ký tự'],
    ['description', tooLong(request.description, DECK_LIMITS.description), 'Mô tả tối đa 500 ký tự'],
  ]);
}

export function validateCard(request: CardRequest): void {
  const examples = request.examples ?? [];
  validate([
    ['word', isBlank(request.word), 'Vui lòng nhập từ'],
    ['word', tooLong(request.word, CARD_LIMITS.word), 'Từ tối đa 100 ký tự'],
    ['partOfSpeech', !request.partOfSpeech, 'Vui lòng chọn loại từ'],
    ['phonetic', tooLong(request.phonetic, CARD_LIMITS.phonetic), 'Phiên âm tối đa 100 ký tự'],
    ['definition', isBlank(request.definition), 'Vui lòng nhập nghĩa'],
    ['definition', tooLong(request.definition, CARD_LIMITS.definition), 'Nghĩa tối đa 255 ký tự'],
    ['examples', examples.length > CARD_LIMITS.maxExamples, 'Tối đa 5 câu ví dụ'],
    ['examples', examples.some((e) => tooLong(e, CARD_LIMITS.example)), 'Mỗi câu ví dụ tối đa 500 ký tự'],
  ]);
}

export function cleanExamples(examples: string[] | null | undefined): string[] {
  return (examples ?? []).map((e) => e.trim()).filter((e) => e.length > 0);
}
