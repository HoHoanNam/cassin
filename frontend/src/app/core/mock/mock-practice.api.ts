import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { PracticeApi } from '../api/practice.api';
import {
  DayStat,
  FillBlankQuestion,
  MistakeItem,
  ModeStat,
  PRACTICE_LIMITS,
  PracticeAnswer,
  PracticeMode,
  PracticeResult,
  PracticeStats,
  SentenceBuildQuestion,
} from '../models/practice.models';
import { addDays, toIsoDate, todayIso } from '../util/dates';
import { parseExample, plainExample } from '../util/example-markup';
import { DbSense, MockDb } from './mock-db';
import { MockSession } from './mock-session';
import { businessError, notFound, shuffle, simulate, validate } from './mock-utils';
import { MockVocab } from './mock-vocab';

const DAY_MS = 86_400_000;

/**
 * [THÊM] Giả lập PracticeService + PracticeStatsService + MistakeService của backend: cùng luật chấm, cùng mã lỗi.
 *
 *   Điền từ : câu PHẢI có đánh dấu [từ đích]; so sánh "lỏng" (hoa thường, dấu câu cuối, nháy cong)
 *   Xếp câu : câu 5–15 mảnh (tách theo khoảng trắng, dấu câu dính theo chữ)
 *   Mỗi nghĩa tối đa 1 câu / lượt, tối đa 30 câu
 *   Sai → nghĩa đến hạn ôn ngày mai (nếu đang học). Đúng → không đổi hộp.
 */
@Injectable()
export class MockPracticeApi extends PracticeApi {
  private readonly db = inject(MockDb);
  private readonly session = inject(MockSession);
  private readonly vocab = inject(MockVocab);

  fillBlank(deckId: number | null, count: number): Observable<FillBlankQuestion[]> {
    return simulate(() => {
      const candidates: FillBlankQuestion[] = [];
      for (const sense of this.practiceSenses(deckId, this.session.userId())) {
        sense.examples.forEach((raw, exampleIndex) => {
          const parts = parseExample(raw);
          if (parts) {
            candidates.push({
              senseId: sense.id,
              exampleIndex,
              sentence: `${parts.before}____${parts.after}`,
              partOfSpeech: sense.partOfSpeech,
              definition: sense.definition,
              firstLetter: parts.target.charAt(0).toLowerCase(),
              answerLength: parts.target.length,
            });
          }
        });
      }
      return pickOnePerSense(candidates, count);
    });
  }

  sentenceBuild(deckId: number | null, count: number): Observable<SentenceBuildQuestion[]> {
    return simulate(() => {
      const candidates: SentenceBuildQuestion[] = [];
      for (const sense of this.practiceSenses(deckId, this.session.userId())) {
        const term = this.db.words.find((w) => w.id === sense.wordId)?.term ?? '';
        sense.examples.forEach((raw, exampleIndex) => {
          const pieces = tokens(plainExample(raw));
          if (pieces.length >= PRACTICE_LIMITS.minTokens && pieces.length <= PRACTICE_LIMITS.maxTokens) {
            candidates.push({ senseId: sense.id, exampleIndex, term, definition: sense.definition, tokens: shuffled(pieces) });
          }
        });
      }
      return pickOnePerSense(candidates, count);
    });
  }

  answer(answer: PracticeAnswer): Observable<PracticeResult> {
    return simulate(() => {
      const userId = this.session.userId();
      validate([
        ['answer', answer.answer == null, 'Vui lòng nhập câu trả lời'],
        ['answer', (answer.answer ?? '').length > 500, 'Câu trả lời tối đa 500 ký tự'],
        ['durationMs', answer.durationMs < 0 || answer.durationMs > PRACTICE_LIMITS.maxDurationMs, 'Thời gian không hợp lệ'],
      ]);
      const sense = this.db.senses.find((s) => s.id === answer.senseId);
      const word = sense && this.db.words.find((w) => w.id === sense.wordId);
      if (!sense || !word || (word.ownerId !== null && word.ownerId !== userId)) {
        throw notFound('SENSE_NOT_FOUND', 'Không tìm thấy nghĩa');
      }
      const raw = sense.examples[answer.exampleIndex];
      if (raw === undefined) {
        throw notPracticable();
      }

      let result: PracticeResult;
      if (answer.mode === 'FILL_BLANK') {
        const parts = parseExample(raw);
        if (!parts) {
          throw notPracticable();
        }
        const correct = normalize(answer.answer) === normalize(parts.target);
        const hint =
          !correct && normalize(answer.answer) === normalize(word.term) ? 'Đúng từ rồi, nhưng cần chia đúng dạng trong câu' : null;
        result = { correct, expected: parts.target, hint };
      } else if (answer.mode === 'SENTENCE_BUILD') {
        const plain = plainExample(raw);
        result = { correct: normalize(answer.answer) === normalize(plain), expected: plain, hint: null };
      } else {
        throw businessError('INVALID_MODE', 'Thẻ lật chấm ở POST /api/senses/{id}/review');
      }

      this.db.reviewEvents.push({
        id: this.db.nextId(),
        userId,
        senseId: sense.id,
        mode: answer.mode,
        correct: result.correct,
        durationMs: answer.durationMs,
        reviewedAt: new Date().toISOString(),
      });
      if (!result.correct) {
        // CardProgress.dueSoon: kéo hạn về NGÀY MAI nếu đang xa hơn, không đổi hộp. Nghĩa chưa học thì bỏ qua.
        const progress = this.db.progresses.find((p) => p.userId === userId && p.senseId === sense.id);
        const tomorrow = addDays(todayIso(), 1);
        if (progress && progress.dueDate > tomorrow) {
          progress.dueDate = tomorrow;
        }
      }
      this.db.save();
      return result;
    });
  }

  stats(days: number): Observable<PracticeStats> {
    return simulate(() => {
      const userId = this.session.userId();
      checkDays(days);
      const today = todayIso();
      const fromDate = addDays(today, -(days - 1)); // "7 ngày" = hôm nay + 6 ngày trước
      const events = this.db.reviewEvents.filter((e) => {
        const day = toIsoDate(new Date(e.reviewedAt));
        return e.userId === userId && day >= fromDate && day <= today;
      });

      // statsByMode: chỉ chế độ ĐÃ luyện, sắp theo tên (ORDER BY e.mode, enum lưu dạng chữ)
      const byModeMap = new Map<PracticeMode, ModeStat>();
      for (const e of events) {
        const stat = byModeMap.get(e.mode) ?? { mode: e.mode, total: 0, correct: 0 };
        stat.total++;
        stat.correct += e.correct ? 1 : 0;
        byModeMap.set(e.mode, stat);
      }
      const byMode = [...byModeMap.values()].sort((a, b) => a.mode.localeCompare(b.mode));

      // statsByDay: ĐỦ mọi ngày (generate_series), kể cả ngày không luyện
      const daily: DayStat[] = [];
      for (let i = 0; i < days; i++) {
        const date = addDays(fromDate, i);
        const ofDay = events.filter((e) => toIsoDate(new Date(e.reviewedAt)) === date);
        daily.push({ date, total: ofDay.length, correct: ofDay.filter((e) => e.correct).length });
      }

      const total = byMode.reduce((sum, m) => sum + m.total, 0);
      const correct = byMode.reduce((sum, m) => sum + m.correct, 0);
      return { total, correct, accuracy: total === 0 ? 0 : correct / total, byMode, daily };
    });
  }

  mistakes(days: number, limit: number): Observable<MistakeItem[]> {
    return simulate(() => {
      const userId = this.session.userId();
      checkDays(days);
      const since = Date.now() - days * DAY_MS; // tròn 24 giờ mỗi ngày, giống MistakeService
      const bySense = new Map<number, { wrong: number; lastWrong: string | null; lastRight: string | null }>();
      for (const e of this.db.reviewEvents) {
        if (e.userId !== userId || Date.parse(e.reviewedAt) < since) {
          continue;
        }
        const row = bySense.get(e.senseId) ?? { wrong: 0, lastWrong: null, lastRight: null };
        if (e.correct) {
          row.lastRight = max(row.lastRight, e.reviewedAt);
        } else {
          row.wrong++;
          row.lastWrong = max(row.lastWrong, e.reviewedAt);
        }
        bySense.set(e.senseId, row);
      }

      const items: MistakeItem[] = [];
      for (const [senseId, row] of bySense) {
        // HAVING: có sai, và lần sai gần nhất SAU lần đúng gần nhất (đã làm đúng lại thì coi như đã sửa)
        if (row.lastWrong === null || (row.lastRight !== null && row.lastRight >= row.lastWrong)) {
          continue;
        }
        const sense = this.db.senses.find((s) => s.id === senseId);
        const word = sense && this.db.words.find((w) => w.id === sense.wordId);
        if (sense && word) {
          items.push({
            senseId,
            wordId: word.id,
            term: word.term,
            partOfSpeech: sense.partOfSpeech,
            definition: sense.definition,
            wrongCount: row.wrong,
            lastWrongAt: row.lastWrong,
          });
        }
      }
      items.sort((a, b) => b.wrongCount - a.wrongCount || b.lastWrongAt.localeCompare(a.lastWrongAt));
      return items.slice(0, Math.min(Math.max(limit, 1), PRACTICE_LIMITS.mistakeMaxLimit));
    });
  }

  // ===== helper =====

  /** WordService.practiceSenses: nghĩa CÓ câu ví dụ, trong một bộ (đọc được) hoặc trong sổ từ. */
  private practiceSenses(deckId: number | null, userId: number): DbSense[] {
    let wordIds: Set<number>;
    if (deckId !== null) {
      this.vocab.readableDeck(deckId, userId); // bộ không đọc được → 404 DECK_NOT_FOUND
      wordIds = this.vocab.wordIdsIn(deckId);
    } else {
      wordIds = new Set(this.vocab.notebookWords(userId).map((w) => w.id));
    }
    return this.db.senses.filter((s) => wordIds.has(s.wordId) && s.examples.length > 0);
  }
}

/** PracticeService.tokens: tách theo khoảng trắng, dấu câu dính theo chữ ("lot."). */
function tokens(sentence: string): string[] {
  const trimmed = sentence.trim();
  return trimmed ? trimmed.split(/\s+/) : [];
}

/** PracticeService.shuffled: xáo, và đảm bảo KHÁC thứ tự gốc nếu có thể. */
function shuffled(pieces: string[]): string[] {
  if (new Set(pieces).size < 2) {
    return [...pieces];
  }
  let copy: string[];
  do {
    copy = shuffle(pieces);
  } while (copy.join('\u0000') === pieces.join('\u0000'));
  return copy;
}

/** PracticeService.normalize: bỏ khoảng trắng thừa, hoa thường, dấu câu cuối; nháy cong → thẳng. */
function normalize(value: string | null | undefined): string {
  return (value ?? '')
    .replace(/’/g, "'")
    .trim()
    .replace(/\s+/g, ' ')
    .replace(/[.,!?;:]+$/, '')
    .toLowerCase();
}

/** PracticeService.pickOnePerSense: xáo, mỗi nghĩa tối đa 1 câu, lấy clamp(count, 1, 30) câu. */
function pickOnePerSense<T extends { senseId: number }>(candidates: T[], count: number): T[] {
  const limit = Math.min(Math.max(count, 1), PRACTICE_LIMITS.maxQuestions);
  const used = new Set<number>();
  const picked: T[] = [];
  for (const question of shuffle(candidates)) {
    if (picked.length === limit) {
      break;
    }
    if (!used.has(question.senseId)) {
      used.add(question.senseId);
      picked.push(question);
    }
  }
  return picked;
}

function checkDays(days: number): void {
  if (days < 1 || days > PRACTICE_LIMITS.mistakeMaxDays) {
    throw businessError('INVALID_DAYS', `Số ngày phải từ 1 đến ${PRACTICE_LIMITS.mistakeMaxDays}`);
  }
}

function notPracticable() {
  return businessError('NOT_PRACTICABLE', 'Câu ví dụ này không dùng để luyện được');
}

function max(a: string | null, b: string): string {
  return a === null || b > a ? b : a;
}
