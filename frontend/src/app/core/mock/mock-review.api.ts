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
import { DbCard, DbProgress, MockDb } from './mock-db';
import { toCard } from './mock-deck.api';
import { MockSession } from './mock-session';
import { conflict, notFound, simulate } from './mock-utils';

const MAX_DUE_PER_QUEUE = 100;
const MAX_NEW_PER_QUEUE = 50;

/** Giả lập ReviewService + luật Leitner trong CardProgress ở backend. */
@Injectable()
export class MockReviewApi extends ReviewApi {
  private readonly db = inject(MockDb);
  private readonly session = inject(MockSession);

  deckQueue(deckId: number, newLimit: number): Observable<ReviewCard[]> {
    return simulate(() => {
      const userId = this.session.userId();
      const deck = this.db.decks.find((d) => d.id === deckId);
      if (!deck || (deck.deckType !== 'SYSTEM_DECK' && deck.ownerId !== userId)) {
        throw notFound('DECK_NOT_FOUND', 'Không tìm thấy bộ từ');
      }
      return this.buildQueue(userId, new Set([deckId]), newLimit);
    });
  }

  allQueue(newLimit: number): Observable<ReviewCard[]> {
    return simulate(() => {
      const userId = this.session.userId();
      const readable = this.db.decks
        .filter((d) => d.deckType === 'SYSTEM_DECK' || d.ownerId === userId)
        .map((d) => d.id);
      return this.buildQueue(userId, new Set(readable), newLimit);
    });
  }

  review(cardId: number, remembered: boolean): Observable<ReviewResult> {
    return simulate(() => {
      const userId = this.session.userId();
      const card = this.db.cards.find((c) => c.id === cardId);
      const deck = card && this.db.decks.find((d) => d.id === card.deckId);
      if (!card || !deck || (deck.deckType !== 'SYSTEM_DECK' && deck.ownerId !== userId)) {
        throw notFound('CARD_NOT_FOUND', 'Không tìm thấy thẻ');
      }
      const today = todayIso();
      let progress = this.db.progresses.find((p) => p.userId === userId && p.cardId === cardId);
      const isNew = !progress;
      progress ??= {
        userId,
        cardId,
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
        cardId,
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

  /** Thẻ đến hạn trước (quá hạn lâu nhất lên đầu), rồi tới tối đa newLimit thẻ mới. */
  private buildQueue(userId: number, deckIds: Set<number>, newLimit: number): ReviewCard[] {
    const today = todayIso();
    const cards = this.db.cards.filter((c) => deckIds.has(c.deckId));
    const progressByCard = new Map<number, DbProgress>(
      this.db.progresses.filter((p) => p.userId === userId).map((p) => [p.cardId, p]),
    );

    const due = cards
      .map((card) => ({ card, progress: progressByCard.get(card.id) }))
      .filter((x): x is { card: DbCard; progress: DbProgress } => !!x.progress && x.progress.dueDate <= today)
      .sort((a, b) => a.progress.dueDate.localeCompare(b.progress.dueDate) || a.progress.box - b.progress.box)
      .slice(0, MAX_DUE_PER_QUEUE)
      .map(({ card, progress }) => ({ card: toCard(card), box: progress.box, isNew: false }));

    const limit = Math.min(Math.max(newLimit, 0), MAX_NEW_PER_QUEUE);
    const fresh = cards
      .filter((c) => !progressByCard.has(c.id))
      .sort((a, b) => a.createdAt.localeCompare(b.createdAt))
      .slice(0, limit)
      .map((card) => ({ card: toCard(card), box: MIN_BOX, isNew: true }));

    return [...due, ...fresh];
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
