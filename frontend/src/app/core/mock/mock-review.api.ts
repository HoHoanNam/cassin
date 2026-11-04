import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { ReviewApi } from '../api/review.api';
import {
  BOX_INTERVAL_DAYS,
  MAX_BOX,
  MIN_BOX,
  ReviewCard,
  ReviewResult,
  ReviewStats,
} from '../models/review.models';
import { addDays, todayIso } from '../util/dates';
import { plainExample } from '../util/example-markup';
import { DbProgress, DbSense, MockDb } from './mock-db';
import { MockSession } from './mock-session';
import { conflict, notFound, simulate } from './mock-utils';
import { MockVocab } from './mock-vocab';

const MAX_DUE_PER_QUEUE = 100;
const MAX_NEW_PER_QUEUE = 50;

/**
 * Giả lập ReviewService + luật Leitner trong CardProgress ở backend.
 * [V10] Đơn vị ôn là NGHĨA. deckId = null: ôn cả sổ từ.
 */
@Injectable()
export class MockReviewApi extends ReviewApi {
  private readonly db = inject(MockDb);
  private readonly vocab = inject(MockVocab);
  private readonly session = inject(MockSession);

  queue(deckId: number | null, newLimit: number): Observable<ReviewCard[]> {
    return simulate(() => {
      const userId = this.session.userId();
      let wordIds: Set<number>;
      if (deckId !== null) {
        this.vocab.readableDeck(deckId, userId);
        wordIds = this.vocab.wordIdsIn(deckId);
      } else {
        wordIds = new Set(this.vocab.notebookWords(userId).map((w) => w.id));
      }
      return this.buildQueue(userId, wordIds, newLimit);
    });
  }

  // durationMs chỉ để khớp hợp đồng: mock chưa có bảng review_events.
  review(senseId: number, remembered: boolean, _durationMs: number): Observable<ReviewResult> {
    return simulate(() => {
      const userId = this.session.userId();
      const sense = this.db.senses.find((s) => s.id === senseId);
      const word = sense && this.db.words.find((w) => w.id === sense.wordId);
      // WordService.findReadableSense: nghĩa của từ mình hoặc từ hệ thống
      if (!sense || !word || (word.ownerId !== null && word.ownerId !== userId)) {
        throw notFound('SENSE_NOT_FOUND', 'Không tìm thấy nghĩa');
      }
      const today = todayIso();
      let progress = this.db.progresses.find((p) => p.userId === userId && p.senseId === senseId);
      const isNew = !progress;
      progress ??= {
        userId,
        senseId,
        box: MIN_BOX,
        dueDate: today,
        lastReviewedOn: null,
        reviewCount: 0,
        lapseCount: 0,
      };

      // Hai luật chặn, giống CardProgress.review(...)
      if (progress.lastReviewedOn === today) {
        throw conflict('REVIEW_NOT_ALLOWED', 'Thẻ này đã được ôn hôm nay');
      }
      if (progress.dueDate > today) {
        throw conflict('REVIEW_NOT_ALLOWED', 'Thẻ này chưa đến hạn ôn');
      }

      if (remembered) {
        progress.box = Math.min(progress.box + 1, MAX_BOX);
      } else {
        progress.box = MIN_BOX;
        progress.lapseCount++;
      }
      progress.dueDate = addDays(today, BOX_INTERVAL_DAYS[progress.box]);
      progress.lastReviewedOn = today;
      progress.reviewCount++;

      if (isNew) {
        this.db.progresses.push(progress);
      }
      this.db.markStudied(userId, today);
      this.db.save();
      return {
        senseId,
        box: progress.box,
        dueDate: progress.dueDate,
        reviewCount: progress.reviewCount,
        lapseCount: progress.lapseCount,
      };
    });
  }

  stats(): Observable<ReviewStats> {
    return simulate(() => {
      const userId = this.session.userId();
      const today = todayIso();
      const mine = this.db.progresses.filter((p) => p.userId === userId);
      const boxes = [1, 2, 3, 4, 5].map((box) => ({ box, total: mine.filter((p) => p.box === box).length }));
      return {
        dueToday: mine.filter((p) => p.dueDate <= today).length,
        boxes,
        streakDays: streak(this.db.studyDays(userId), today),
      };
    });
  }

  /** Nghĩa đến hạn trước (quá hạn lâu nhất lên đầu), rồi tới tối đa newLimit nghĩa mới. */
  private buildQueue(userId: number, wordIds: Set<number>, newLimit: number): ReviewCard[] {
    const today = todayIso();
    const senses = this.db.senses.filter((s) => wordIds.has(s.wordId));
    const progressBySense = new Map<number, DbProgress>(
      this.db.progresses.filter((p) => p.userId === userId).map((p) => [p.senseId, p]),
    );

    const due = senses
      .map((sense) => ({ sense, progress: progressBySense.get(sense.id) }))
      .filter((x): x is { sense: DbSense; progress: DbProgress } => !!x.progress && x.progress.dueDate <= today)
      .sort((a, b) => a.progress.dueDate.localeCompare(b.progress.dueDate) || a.progress.box - b.progress.box)
      .slice(0, MAX_DUE_PER_QUEUE)
      .map(({ sense, progress }) => this.toCard(sense, progress.box, false));

    const createdAt = new Map(this.db.words.map((w) => [w.id, w.createdAt] as const));
    const limit = Math.min(Math.max(newLimit, 0), MAX_NEW_PER_QUEUE);
    const fresh = senses
      .filter((s) => !progressBySense.has(s.id))
      .sort((a, b) => (createdAt.get(a.wordId) ?? '').localeCompare(createdAt.get(b.wordId) ?? '') || a.position - b.position)
      .slice(0, limit)
      .map((sense) => this.toCard(sense, MIN_BOX, true));

    return [...due, ...fresh];
  }

  /** ReviewCardResponse.of: kèm các nghĩa KHÁC của cùng từ, câu ví dụ đã bỏ dấu [ ]. */
  private toCard(sense: DbSense, box: number, isNew: boolean): ReviewCard {
    const word = this.db.words.find((w) => w.id === sense.wordId)!;
    return {
      senseId: sense.id,
      wordId: word.id,
      term: word.term,
      phonetic: word.phonetic,
      partOfSpeech: sense.partOfSpeech,
      definition: sense.definition,
      examples: sense.examples.map(plainExample),
      otherSenses: this.vocab
        .sensesOf(word.id)
        .filter((s) => s.id !== sense.id)
        .map((s) => ({ partOfSpeech: s.partOfSpeech, definition: s.definition })),
      box,
      isNew,
    };
  }
}

/** Số ngày học liên tiếp tính tới hôm nay (hoặc hôm qua, nếu hôm nay chưa học). */
function streak(days: string[], today: string): number {
  const set = new Set(days);
  let cursor = set.has(today) ? today : addDays(today, -1);
  let count = 0;
  while (set.has(cursor)) {
    count++;
    cursor = addDays(cursor, -1);
  }
  return count;
}
