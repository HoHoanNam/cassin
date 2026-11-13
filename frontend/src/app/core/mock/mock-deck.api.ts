import { Injectable, inject } from '@angular/core';
import { Observable, from, switchMap } from 'rxjs';

import { DeckApi } from '../api/deck.api';
import { PageResponse } from '../models/common.models';
import { ApiError } from '../models/common.models';
import { Deck, DeckRequest, PartOfSpeech, WORD_LIMITS, WordSummary } from '../models/deck.models';
import { IMPORT_MAX_BYTES, IMPORT_MAX_ROWS, ImportError, ImportResult } from '../models/import.models';
import { exampleMarkupError } from '../util/example-markup';
import { PART_OF_SPEECH_OPTIONS } from '../util/part-of-speech';
import { parseCsv } from './mock-csv';
import { DbDeck, MockDb } from './mock-db';
import { MockSession } from './mock-session';
import { businessError, simulate } from './mock-utils';
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

  // ===================== Nhập CSV (giả lập WordImportService) =====================

  importWords(deckId: number, file: File, dryRun: boolean): Observable<ImportResult> {
    return from(file.text()).pipe(switchMap((text) => simulate(() => this.runImport(deckId, file.size, text, dryRun))));
  }

  private runImport(deckId: number, size: number, text: string, dryRun: boolean): ImportResult {
    if (size > IMPORT_MAX_BYTES) {
      throw new ApiError(413, 'FILE_TOO_LARGE', 'File tối đa 1MB');
    }
    const userId = this.session.userId();
    const deck = this.vocab.editableDeck(deckId, userId);

    // ---- Lỗi của CẢ FILE → 400 ----
    const table = parseCsv(text);
    if (table.header.length === 0) {
      throw businessError('EMPTY_FILE', 'Vui lòng chọn một file CSV có dữ liệu');
    }
    const missing = ['term', 'part_of_speech', 'definition'].filter((c) => !table.header.includes(c));
    if (missing.length > 0) {
      throw businessError('INVALID_FILE', `File thiếu cột: ${missing.join(', ')}. Dòng đầu phải là tiêu đề: term, phonetic, part_of_speech, definition, example_1...`);
    }
    if (table.rows.length > IMPORT_MAX_ROWS) {
      throw businessError('TOO_MANY_ROWS', `Mỗi file tối đa ${IMPORT_MAX_ROWS} dòng. Hãy chia thành nhiều file.`);
    }
    if (table.rows.length === 0) {
      throw businessError('EMPTY_FILE', 'File chỉ có dòng tiêu đề, chưa có từ nào');
    }

    // ---- Lỗi từng DÒNG: gom hết, không ném ----
    const validPos = new Set(PART_OF_SPEECH_OPTIONS.map((o) => o.value));
    const errors: ImportError[] = [];
    type Row = { line: number; term: string; phonetic: string; pos: PartOfSpeech; definition: string; examples: string[] };
    const valid: Row[] = [];
    table.rows.forEach((cells, index) => {
      const line = index + 2;   // dòng 1 là tiêu đề
      const before = errors.length;
      const term = cells['term'] ?? '';
      const posRaw = cells['part_of_speech'] ?? '';
      const pos = posRaw.trim().toUpperCase().replace(/[\s-]+/g, '_') as PartOfSpeech;
      const definition = cells['definition'] ?? '';
      const phonetic = cells['phonetic'] ?? '';
      if (!term) errors.push({ line, field: 'term', message: 'Thiếu từ' });
      else if (term.length > WORD_LIMITS.term) errors.push({ line, field: 'term', message: `Từ tối đa ${WORD_LIMITS.term} ký tự` });
      if (phonetic.length > WORD_LIMITS.phonetic) errors.push({ line, field: 'phonetic', message: `Phiên âm tối đa ${WORD_LIMITS.phonetic} ký tự` });
      if (!validPos.has(pos)) {
        errors.push({ line, field: 'part_of_speech', message: `Loại từ không hợp lệ: "${posRaw}". Dùng một trong: ${[...validPos].join(', ')}` });
      }
      if (!definition) errors.push({ line, field: 'definition', message: 'Thiếu nghĩa' });
      else if (definition.length > WORD_LIMITS.definition) errors.push({ line, field: 'definition', message: `Nghĩa tối đa ${WORD_LIMITS.definition} ký tự` });
      const examples: string[] = [];
      for (let i = 1; i <= WORD_LIMITS.maxExamples; i++) {
        const example = cells[`example_${i}`] ?? '';
        if (!example) continue;
        const markupError = example.length > WORD_LIMITS.example ? `Câu ví dụ tối đa ${WORD_LIMITS.example} ký tự` : exampleMarkupError(example);
        if (markupError) errors.push({ line, field: `example_${i}`, message: markupError });
        examples.push(example);
      }
      if (errors.length === before) valid.push({ line, term, phonetic, pos, definition, examples });
    });

    // ---- Gộp các dòng cùng term (không phân biệt hoa thường) → lỗi của CẢ TỪ ----
    const groups = new Map<string, Row[]>();
    valid.forEach((r) => groups.set(r.term.toLowerCase(), [...(groups.get(r.term.toLowerCase()) ?? []), r]));
    for (const rows of groups.values()) {
      const seen = new Set<string>();
      if (rows.length > WORD_LIMITS.maxSenses) {
        errors.push({ line: rows[0].line, field: null, message: `Mỗi từ tối đa ${WORD_LIMITS.maxSenses} nghĩa` });
      }
      for (const r of rows) {
        const key = r.definition.toLowerCase();
        if (seen.has(key)) {
          errors.push({ line: rows[0].line, field: null, message: `Hai nghĩa không được trùng nhau: ${r.definition}` });
          break;
        }
        seen.add(key);
      }
    }
    if (errors.length > 0) {
      return { imported: false, words: 0, senses: 0, skipped: [], errors: errors.sort((a, b) => a.line - b.line) };
    }

    // ---- Từ đã có trong sổ của tôi → bỏ qua ----
    const ownTerms = new Set(this.db.words.filter((w) => w.ownerId === userId).map((w) => w.term.toLowerCase()));
    const skipped: string[] = [];
    const fresh: Row[][] = [];
    for (const [key, rows] of groups) {
      if (ownTerms.has(key)) skipped.push(rows[0].term);
      else fresh.push(rows);
    }
    const senses = fresh.reduce((sum, rows) => sum + rows.length, 0);
    if (dryRun) {
      return { imported: false, words: fresh.length, senses, skipped, errors: [] };
    }
    for (const rows of fresh) {
      const word = this.vocab.createWord(userId, {
        term: rows[0].term,
        phonetic: rows[0].phonetic || null,
        senses: rows.map((r) => ({ id: null, partOfSpeech: r.pos, definition: r.definition, examples: r.examples })),
        deckIds: [],
      });
      this.vocab.linkWord(deck, word);
    }
    this.db.save();
    return { imported: true, words: fresh.length, senses, skipped, errors: [] };
  }
}
