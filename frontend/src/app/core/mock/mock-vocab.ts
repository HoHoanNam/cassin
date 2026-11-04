import { Injectable, inject } from '@angular/core';

import { PageResponse } from '../models/common.models';
import {
  DECK_LIMITS,
  Deck,
  DeckRef,
  DeckRequest,
  WORD_LIMITS,
  Word,
  WordRequest,
  WordSummary,
} from '../models/deck.models';
import { todayIso } from '../util/dates';
import { exampleMarkupError } from '../util/example-markup';
import { DbDeck, DbSense, DbWord, MockDb } from './mock-db';
import { businessError, forbidden, isBlank, notFound, tooLong, validate } from './mock-utils';

/**
 * [V10] Luật từ vựng dùng chung cho MockDeckApi, MockWordApi, MockReviewApi, MockAdminApi.
 * Mỗi hàm ở đây ứng với một chỗ trong backend (ghi chú bên cạnh), để mock chạy y như API thật:
 *
 *   Từ đọc được: của mình HOẶC từ hệ thống.     Sửa được: chỉ từ của mình (403 WORD_READ_ONLY)
 *   Bộ hệ thống chỉ chứa từ hệ thống.           Bộ cá nhân chứa từ của chủ bộ + từ hệ thống
 *   Sổ từ = từ của mình ∪ từ hệ thống trong bộ của mình ∪ từ hệ thống mình đang học
 */
@Injectable({ providedIn: 'root' })
export class MockVocab {
  private readonly db = inject(MockDb);

  // ===================== Bộ (DeckService) =====================

  /** DeckService.findReadable: bộ hệ thống hoặc bộ của mình, không thì 404. */
  readableDeck(deckId: number, userId: number): DbDeck {
    const deck = this.db.decks.find((d) => d.id === deckId);
    if (!deck || (deck.deckType !== 'SYSTEM_DECK' && deck.ownerId !== userId)) {
      throw notFound('DECK_NOT_FOUND', 'Không tìm thấy bộ từ');
    }
    return deck;
  }

  /** DeckService.findEditable */
  editableDeck(deckId: number, userId: number): DbDeck {
    const deck = this.readableDeck(deckId, userId);
    if (deck.ownerId !== userId) {
      throw forbidden('DECK_READ_ONLY', 'Bộ từ hệ thống chỉ để ôn, không sửa được');
    }
    return deck;
  }

  /** Bộ hệ thống (admin). Bộ cá nhân → 404, giống AdminDeckService.findSystemDeck. */
  systemDeck(deckId: number): DbDeck {
    const deck = this.db.decks.find((d) => d.id === deckId && d.deckType === 'SYSTEM_DECK');
    if (!deck) {
      throw notFound('DECK_NOT_FOUND', 'Không tìm thấy bộ từ');
    }
    return deck;
  }

  /** DeckResponse.from: số từ + số NGHĨA đến hạn / chưa học. userId = null (admin): due / new = 0. */
  toDeck(deck: DbDeck, userId: number | null): Deck {
    const wordIds = this.wordIdsIn(deck.id);
    let dueCount = 0;
    let newCount = 0;
    if (userId !== null) {
      const today = todayIso();
      const progressBySense = new Map(
        this.db.progresses.filter((p) => p.userId === userId).map((p) => [p.senseId, p] as const),
      );
      for (const sense of this.db.senses.filter((s) => wordIds.has(s.wordId))) {
        const progress = progressBySense.get(sense.id);
        if (!progress) {
          newCount++;
        } else if (progress.dueDate <= today) {
          dueCount++;
        }
      }
    }
    return {
      id: deck.id,
      deckName: deck.deckName,
      description: deck.description,
      deckType: deck.deckType,
      wordCount: wordIds.size,
      dueCount,
      newCount,
      editable: userId !== null && deck.ownerId === userId,
      updatedAt: deck.updatedAt,
    };
  }

  wordIdsIn(deckId: number): Set<number> {
    return new Set(this.db.deckWords.filter((dw) => dw.deckId === deckId).map((dw) => dw.wordId));
  }

  /** DeckService.linkWord: kiểm tra Deck.canContain, rồi chèn deck_words (đã có thì thôi). */
  linkWord(deck: DbDeck, word: DbWord): void {
    const allowed =
      deck.deckType === 'SYSTEM_DECK'
        ? word.ownerId === null
        : word.ownerId === null || word.ownerId === deck.ownerId;
    if (!allowed) {
      throw businessError('WORD_NOT_ALLOWED', 'Bộ hệ thống chỉ chứa từ hệ thống');
    }
    if (!this.db.deckWords.some((dw) => dw.deckId === deck.id && dw.wordId === word.id)) {
      this.db.deckWords.push({ deckId: deck.id, wordId: word.id });
    }
    deck.updatedAt = new Date().toISOString();
  }

  /** deckRepository.removeWord: chỉ xóa dòng deck_words. Có xóa thì cập nhật thời gian bộ. */
  unlinkWord(deck: DbDeck, wordId: number): void {
    const before = this.db.deckWords.length;
    this.db.removeWhere('deckWords', (dw) => dw.deckId === deck.id && dw.wordId === wordId);
    if (this.db.deckWords.length < before) {
      deck.updatedAt = new Date().toISOString();
    }
  }

  // ===================== Từ (WordService) =====================

  /** DeckService.findReadableWord: từ của mình hoặc từ hệ thống, không thì 404. */
  readableWord(wordId: number, userId: number): DbWord {
    const word = this.db.words.find((w) => w.id === wordId);
    if (!word || (word.ownerId !== null && word.ownerId !== userId)) {
      throw notFound('WORD_NOT_FOUND', 'Không tìm thấy từ');
    }
    return word;
  }

  /** WordService.findEditableWord */
  editableWord(wordId: number, userId: number): DbWord {
    const word = this.readableWord(wordId, userId);
    if (word.ownerId !== userId) {
      throw forbidden('WORD_READ_ONLY', 'Từ hệ thống chỉ để học, không sửa được');
    }
    return word;
  }

  /** AdminDeckService.findSystemWord */
  systemWord(wordId: number): DbWord {
    const word = this.db.words.find((w) => w.id === wordId && w.ownerId === null);
    if (!word) {
      throw notFound('WORD_NOT_FOUND', 'Không tìm thấy từ');
    }
    return word;
  }

  sensesOf(wordId: number): DbSense[] {
    return this.db.senses
      .filter((s) => s.wordId === wordId)
      .sort((a, b) => a.position - b.position);
  }

  /** WordRepository.findNotebook: 3 nguồn gộp lại. */
  notebookWords(userId: number): DbWord[] {
    const myDeckIds = new Set(this.db.decks.filter((d) => d.ownerId === userId).map((d) => d.id));
    const inMyDecks = new Set(
      this.db.deckWords.filter((dw) => myDeckIds.has(dw.deckId)).map((dw) => dw.wordId),
    );
    const studiedSenses = new Set(
      this.db.progresses.filter((p) => p.userId === userId).map((p) => p.senseId),
    );
    const studiedWords = new Set(
      this.db.senses.filter((s) => studiedSenses.has(s.id)).map((s) => s.wordId),
    );
    return this.db.words.filter(
      (w) =>
        w.ownerId === userId ||
        (w.ownerId === null && (inMyDecks.has(w.id) || studiedWords.has(w.id))),
    );
  }

  /** WordSummary.from */
  toSummary(word: DbWord, userId: number | null): WordSummary {
    return {
      id: word.id,
      term: word.term,
      phonetic: word.phonetic,
      editable: userId !== null && word.ownerId === userId,
      system: word.ownerId === null,
      senses: this.sensesOf(word.id).map((s) => ({
        id: s.id,
        partOfSpeech: s.partOfSpeech,
        definition: s.definition,
      })),
    };
  }

  /** WordResponse.from. decks: bộ mình XEM được (admin: chỉ bộ hệ thống). */
  toWord(word: DbWord, userId: number | null): Word {
    const deckIds = new Set(
      this.db.deckWords.filter((dw) => dw.wordId === word.id).map((dw) => dw.deckId),
    );
    const decks: DeckRef[] = this.db.decks
      .filter(
        (d) =>
          deckIds.has(d.id) &&
          (d.deckType === 'SYSTEM_DECK' || (userId !== null && d.ownerId === userId)),
      )
      .map((d) => ({ id: d.id, deckName: d.deckName }));
    return {
      ...this.toSummary(word, userId),
      senses: this.sensesOf(word.id).map((s) => ({
        id: s.id,
        position: s.position,
        partOfSpeech: s.partOfSpeech,
        definition: s.definition,
        examples: [...s.examples],
      })),
      decks,
    };
  }

  /** Có từ cùng cách viết (không phân biệt hoa thường) của cùng chủ chưa? ownerId = null: từ hệ thống. */
  termExists(ownerId: number | null, term: string, exceptWordId: number | null = null): boolean {
    const wanted = term.trim().toLowerCase();
    return this.db.words.some(
      (w) => w.ownerId === ownerId && w.id !== exceptWordId && w.term.toLowerCase() === wanted,
    );
  }

  /**
   * @Valid trên WordRequest (lỗi theo field) rồi luật của Word.replaceSenses (400 INVALID_WORD).
   * Kiểm tra HẾT trước khi sửa gì, giống backend: không bao giờ sửa dở dang.
   */
  validateWord(request: WordRequest, word: DbWord | null): void {
    const senses = request.senses ?? [];
    const rules: [string, boolean, string][] = [
      ['term', isBlank(request.term), 'Vui lòng nhập từ'],
      ['term', tooLong(request.term, WORD_LIMITS.term), 'Từ tối đa 100 ký tự'],
      ['phonetic', tooLong(request.phonetic, WORD_LIMITS.phonetic), 'Phiên âm tối đa 100 ký tự'],
      ['senses', senses.length === 0, 'Từ phải có ít nhất 1 nghĩa'],
      ['senses', senses.length > WORD_LIMITS.maxSenses, 'Mỗi từ tối đa 10 nghĩa'],
    ];
    senses.forEach((s, i) => {
      rules.push(
        [`senses[${i}].partOfSpeech`, !s.partOfSpeech, 'Vui lòng chọn loại từ'],
        [`senses[${i}].definition`, isBlank(s.definition), 'Vui lòng nhập nghĩa'],
        [
          `senses[${i}].definition`,
          tooLong(s.definition, WORD_LIMITS.definition),
          'Nghĩa tối đa 255 ký tự',
        ],
        [
          `senses[${i}].examples`,
          (s.examples ?? []).length > WORD_LIMITS.maxExamples,
          'Mỗi nghĩa tối đa 5 câu ví dụ',
        ],
        [
          `senses[${i}].examples`,
          (s.examples ?? []).some((e) => tooLong(e, WORD_LIMITS.example)),
          'Mỗi câu ví dụ tối đa 500 ký tự',
        ],
      );
    });
    validate(rules);

    const ownIds = new Set(word ? this.sensesOf(word.id).map((s) => s.id) : []);
    const seenIds = new Set<number>();
    const seenDefinitions = new Set<string>();
    for (const s of senses) {
      if (s.id !== null) {
        if (!ownIds.has(s.id)) {
          throw businessError('INVALID_WORD', 'Nghĩa không thuộc từ này: ' + s.id);
        }
        if (seenIds.has(s.id)) {
          throw businessError('INVALID_WORD', 'Một nghĩa bị gửi hai lần: ' + s.id);
        }
        seenIds.add(s.id);
      }
      const definition = s.definition.trim().toLowerCase();
      if (seenDefinitions.has(definition)) {
        throw businessError(
          'INVALID_WORD',
          'Hai nghĩa không được trùng nhau: ' + s.definition.trim(),
        );
      }
      seenDefinitions.add(definition);
      for (const example of s.examples ?? []) {
        const markupError = exampleMarkupError(example.trim());
        if (markupError) {
          throw businessError('INVALID_WORD', `${markupError}: ${example.trim()}`);
        }
      }
    }
  }

  /** Word.replaceSenses: nghĩa giữ id thì sửa tại chỗ (giữ tiến độ), nghĩa bị bỏ thì xóa kèm tiến độ. */
  replaceSenses(word: DbWord, request: WordRequest): void {
    const keepIds = new Set(request.senses.filter((s) => s.id !== null).map((s) => s.id as number));
    const removed = new Set(
      this.sensesOf(word.id)
        .filter((s) => !keepIds.has(s.id))
        .map((s) => s.id),
    );
    this.db.removeWhere('progresses', (p) => removed.has(p.senseId));
    this.db.removeWhere('senses', (s) => removed.has(s.id));

    request.senses.forEach((input, position) => {
      const examples = (input.examples ?? []).map((e) => e.trim()).filter((e) => e.length > 0);
      const existing =
        input.id !== null ? this.db.senses.find((s) => s.id === input.id) : undefined;
      if (existing) {
        Object.assign(existing, {
          position,
          partOfSpeech: input.partOfSpeech,
          definition: input.definition.trim(),
          examples,
        });
      } else {
        this.db.senses.push({
          id: this.db.nextId(),
          wordId: word.id,
          position,
          partOfSpeech: input.partOfSpeech,
          definition: input.definition.trim(),
          examples,
        });
      }
    });
    word.term = request.term.trim();
    word.phonetic = request.phonetic?.trim() || null;
  }

  /** Tạo từ (chưa có nghĩa) rồi gắn nghĩa. ownerId = null: từ hệ thống. */
  createWord(ownerId: number | null, request: WordRequest): DbWord {
    const word: DbWord = {
      id: this.db.nextId(),
      ownerId,
      term: request.term.trim(),
      phonetic: null,
      createdAt: new Date().toISOString(),
    };
    this.db.words.push(word);
    this.replaceSenses(word, request);
    return word;
  }

  /** Xóa từ: cascade nghĩa, tiến độ, deck_words (giống ON DELETE CASCADE). */
  deleteWord(word: DbWord): void {
    const senseIds = new Set(this.sensesOf(word.id).map((s) => s.id));
    this.db.removeWhere('progresses', (p) => senseIds.has(p.senseId));
    this.db.removeWhere('senses', (s) => s.wordId === word.id);
    this.db.removeWhere('deckWords', (dw) => dw.wordId === word.id);
    this.db.removeWhere('words', (w) => w.id === word.id);
  }

  /** Có học viên nào đã học nghĩa của một từ trong bộ chưa (CardProgressRepository.existsInDeck)? */
  deckInUse(deckId: number): boolean {
    const wordIds = this.wordIdsIn(deckId);
    const senseIds = new Set(this.db.senses.filter((s) => wordIds.has(s.wordId)).map((s) => s.id));
    return this.db.progresses.some((p) => senseIds.has(p.senseId));
  }
}

/** Lọc theo q (không phân biệt hoa thường) rồi cắt trang, giống findNotebook / findInDeck + PageResponse. */
export function pageOf<T extends { term: string }>(
  items: T[],
  q: string,
  page: number,
  size: number,
): PageResponse<T> {
  const wanted = q.trim().toLowerCase();
  const all = wanted ? items.filter((i) => i.term.toLowerCase().includes(wanted)) : items;
  const safePage = Math.max(page, 0);
  const safeSize = Math.min(Math.max(size, 1), 100);
  return {
    content: all.slice(safePage * safeSize, (safePage + 1) * safeSize),
    page: safePage,
    size: safeSize,
    totalElements: all.length,
    totalPages: Math.ceil(all.length / safeSize),
  };
}

export function validateDeck(request: DeckRequest): void {
  validate([
    ['deckName', isBlank(request.deckName), 'Vui lòng nhập tên bộ'],
    ['deckName', tooLong(request.deckName, DECK_LIMITS.deckName), 'Tên bộ tối đa 100 ký tự'],
    [
      'description',
      tooLong(request.description, DECK_LIMITS.description),
      'Mô tả tối đa 500 ký tự',
    ],
  ]);
}
