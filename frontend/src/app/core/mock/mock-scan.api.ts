import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { ScanApi } from '../api/scan.api';
import { SCAN_MAX_LENGTH, ScanItem, ScanResult } from '../models/scan.models';
import { DbWord, MockDb } from './mock-db';
import { candidates, guess, isStopWord, tokenize, Tokenized } from './mock-scan-text';
import { MockSession } from './mock-session';
import { isBlank, simulate, validate } from './mock-utils';

const MAX_ITEMS = 200;
const MAX_EXAMPLE_LENGTH = 500;

interface Occurrence {
  key: string;
  first: number;
  last: number;
  word: DbWord | null;
}

/** Giả lập TextScanService: cùng 6 bước như tài liệu thiết kế, "DB" là MockDb. */
@Injectable()
export class MockScanApi extends ScanApi {
  private readonly db = inject(MockDb);
  private readonly session = inject(MockSession);

  scan(text: string): Observable<ScanResult> {
    return simulate(() => {
      validate([
        ['text', isBlank(text), 'Hãy dán một đoạn tiếng Anh'],
        ['text', text.length > SCAN_MAX_LENGTH, 'Đoạn văn tối đa 5.000 ký tự'],
      ]);
      return this.run(text, this.session.userId());
    });
  }

  private run(text: string, userId: number): ScanResult {
    const tokenized = tokenize(text);
    const tokens = tokenized.tokens;
    const used = new Array<boolean>(tokens.length).fill(false);
    const occurrences: Occurrence[] = [];

    // Từ "nhìn thấy được": của tôi + hệ thống. Từ của tôi ưu tiên khi trùng term.
    const visible = this.db.words
      .filter((w) => w.ownerId === userId || w.ownerId === null)
      .sort((a, b) => Number(a.ownerId === null) - Number(b.ownerId === null));

    // ② cụm nhiều chữ, cụm dài thử trước
    const phrases = visible
      .filter((w) => w.term.includes(' '))
      .sort((a, b) => b.term.split(' ').length - a.term.split(' ').length);
    for (let i = 0; i < tokens.length; i++) {
      for (const phrase of phrases) {
        const parts = phrase.term.toLowerCase().split(' ');
        let match = i + parts.length <= tokens.length;
        for (let k = 0; match && k < parts.length; k++) {
          const t = tokens[i + k];
          match = !used[i + k] && t.sentenceIndex === tokens[i].sentenceIndex && candidates(t.lower).includes(parts[k]);
        }
        if (match) {
          for (let k = 0; k < parts.length; k++) used[i + k] = true;
          occurrences.push({ key: phrase.term.toLowerCase(), first: i, last: i + parts.length - 1, word: phrase });
          break;
        }
      }
    }

    // ③ từ đơn: một lần "truy vấn" cho mọi ứng viên
    const byTerm = new Map<string, DbWord>();
    for (const w of visible) {
      if (!byTerm.has(w.term.toLowerCase())) byTerm.set(w.term.toLowerCase(), w);
    }
    tokens.forEach((t, i) => {
      if (used[i] || isStopWord(t.lower)) return;
      const hit = candidates(t.lower).find((c) => byTerm.has(c));
      occurrences.push({ key: hit ?? guess(t.lower), first: i, last: i, word: hit ? byTerm.get(hit)! : null });
    });

    // ④ gộp theo khóa (thứ tự xuất hiện), bỏ tên riêng
    occurrences.sort((a, b) => a.first - b.first);
    const groups = new Map<string, Occurrence[]>();
    for (const o of occurrences) {
      const g = groups.get(o.key) ?? [];
      g.push(o);
      groups.set(o.key, g);
    }
    for (const [key, g] of groups) {
      const properNoun = g[0].word === null
        && g.every((o) => tokens[o.first].upper)
        && g.some((o) => tokens[o.first].upper && !tokens[o.first].sentenceStart);
      if (properNoun) groups.delete(key);
    }

    // ⑤ đã biết: từ của tôi, hoặc từ hệ thống nằm trong bộ của tôi / đã học một nghĩa
    const myDeckIds = new Set(this.db.decks.filter((d) => d.ownerId === userId).map((d) => d.id));
    const inMyDecks = new Set(this.db.deckWords.filter((dw) => myDeckIds.has(dw.deckId)).map((dw) => dw.wordId));
    const studiedSenses = new Set(this.db.progresses.filter((p) => p.userId === userId).map((p) => p.senseId));
    const studied = new Set(this.db.senses.filter((s) => studiedSenses.has(s.id)).map((s) => s.wordId));
    const isKnown = (w: DbWord | null) => w !== null && (w.ownerId === userId || inMyDecks.has(w.id) || studied.has(w.id));

    // ⑥ kết quả
    let wordCount = 0;
    let knownCount = 0;
    const items: ScanItem[] = [];
    for (const [key, g] of groups) {
      wordCount += g.length;
      const word = g[0].word;
      if (isKnown(word)) {
        knownCount++;
        continue;
      }
      items.push({
        term: word ? word.term : key,
        forms: [...new Set(g.map((o) => surface(tokenized, o).toLowerCase()))],
        count: g.length,
        example: buildExample(tokenized, g[0].first, g[0].last),
        source: word ? 'LIBRARY' : 'NEW',
        wordId: word ? word.id : null,
        senses: word
          ? this.db.senses
              .filter((s) => s.wordId === word.id)
              .sort((a, b) => a.position - b.position)
              .map((s) => ({ partOfSpeech: s.partOfSpeech, definition: s.definition }))
          : [],
      });
    }
    items.sort((a, b) => b.count - a.count);   // Array.sort ổn định: bằng nhau giữ thứ tự xuất hiện
    return {
      wordCount,
      uniqueCount: groups.size,
      knownCount,
      truncated: items.length > MAX_ITEMS,
      items: items.slice(0, MAX_ITEMS),
    };
  }
}

function surface(t: Tokenized, o: Occurrence): string {
  const first = t.tokens[o.first];
  return t.sentences[first.sentenceIndex].slice(first.start, t.tokens[o.last].end);
}

function buildExample(t: Tokenized, first: number, last: number): string | null {
  const a = t.tokens[first];
  const sentence = t.sentences[a.sentenceIndex].replace(/\[/g, '(').replace(/]/g, ')');
  const end = t.tokens[last].end;
  const marked = `${sentence.slice(0, a.start)}[${sentence.slice(a.start, end)}]${sentence.slice(end)}`;
  return marked.length > MAX_EXAMPLE_LENGTH ? null : marked;
}
