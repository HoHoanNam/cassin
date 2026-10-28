import { ChangeDetectionStrategy, Component, computed, inject, input, numberAttribute, signal } from '@angular/core';
import { rxResource } from '@angular/core/rxjs-interop';
import { RouterLink } from '@angular/router';
import { of } from 'rxjs';

import { DeckApi } from '../../core/api/deck.api';
import { ReviewApi } from '../../core/api/review.api';
import { ApiError } from '../../core/models/common.models';
import { MAX_BOX, ReviewCard } from '../../core/models/review.models';
import { StudyStatsStore } from '../../core/state/study-stats.store';
import { ToastService } from '../../core/ui/toast.service';
import { EmptyState } from '../../shared/ui/empty-state/empty-state';
import { ErrorState } from '../../shared/ui/error-state/error-state';
import { Icon } from '../../shared/ui/icon/icon';
import { FlashCard } from './flash-card/flash-card';

/** Số thẻ mới tối đa mỗi buổi ôn một bộ (khớp newLimit mặc định ở backend). */
const NEW_CARDS_PER_SESSION = 10;

/**
 * Chế độ ôn tập trung: không sidebar, chỉ có nút Thoát + thanh tiến độ.
 *   /review               → ôn mọi thẻ đến hạn (mọi bộ)
 *   /decks/:deckId/review → ôn một bộ, kèm tối đa 10 thẻ mới
 *
 * Chấm điểm kiểu "lạc quan": bấm Nhớ/Quên là sang thẻ kế tiếp NGAY, kết quả gửi lên server phía sau.
 * Gửi lỗi thì báo và cho "Thử lại", người học không phải chờ mạng sau mỗi thẻ.
 * Phím tắt: Space/Enter lật thẻ, 1 = Quên, 2 = Nhớ.
 */
@Component({
  selector: 'app-review-page',
  imports: [RouterLink, EmptyState, ErrorState, FlashCard, Icon],
  templateUrl: './review-page.html',
  styleUrl: './review-page.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: { '(document:keydown)': 'onKey($event)' },
})
export class ReviewPage {
  /** Có trên URL /decks/:deckId/review; không có ở /review. */
  readonly deckId = input(undefined, { transform: (v: unknown) => (v == null ? undefined : numberAttribute(v)) });

  private readonly reviewApi = inject(ReviewApi);
  private readonly deckApi = inject(DeckApi);
  private readonly stats = inject(StudyStatsStore);
  private readonly toast = inject(ToastService);

  protected readonly queue = rxResource({
    params: () => ({ deckId: this.deckId() }),
    stream: ({ params }) =>
      params.deckId !== undefined
        ? this.reviewApi.deckQueue(params.deckId, NEW_CARDS_PER_SESSION)
        : this.reviewApi.allQueue(0),
  });

  protected readonly deck = rxResource({
    params: () => this.deckId(),
    stream: ({ params }) => (params !== undefined ? this.deckApi.getDeck(params) : of(null)),
  });

  protected readonly index = signal(0);
  protected readonly flipped = signal(false);
  protected readonly remembered = signal(0);
  protected readonly forgotten = signal(0);
  protected readonly failed = signal(0);

  protected readonly cards = computed<ReviewCard[]>(() => (this.queue.hasValue() ? this.queue.value() : []));
  protected readonly current = computed(() => this.cards()[this.index()] ?? null);
  protected readonly finished = computed(() => this.cards().length > 0 && this.index() >= this.cards().length);
  protected readonly progress = computed(() =>
    this.cards().length ? Math.round((this.index() / this.cards().length) * 100) : 0,
  );
  protected readonly title = computed(() => (this.deck.hasValue() && this.deck.value()?.deckName) || 'Ôn thẻ đến hạn');
  protected readonly exitLink = computed(() => (this.deckId() !== undefined ? ['/decks', this.deckId()] : ['/today']));
  protected readonly nextBoxText = computed(() => {
    const card = this.current();
    if (!card || card.isNew) {
      return 'lên hộp 2';
    }
    return card.box >= MAX_BOX ? 'giữ ở hộp 5' : `lên hộp ${card.box + 1}`;
  });

  protected flip(): void {
    if (this.current()) {
      this.flipped.update((v) => !v);
    }
  }

  protected grade(remembered: boolean): void {
    const item = this.current();
    if (!item || !this.flipped()) {
      return;
    }
    (remembered ? this.remembered : this.forgotten).update((n) => n + 1);
    this.flipped.set(false);
    this.index.update((i) => i + 1);
    this.send(item, remembered);

    if (this.finished()) {
      this.stats.refresh();
    }
  }

  protected onKey(event: KeyboardEvent): void {
    if (event.target instanceof HTMLElement && event.target.closest('input, textarea, select')) {
      return;
    }
    if (!this.current()) {
      return;
    }
    if ((event.key === ' ' || event.key === 'Enter') && !this.flipped()) {
      event.preventDefault();
      this.flip();
    } else if (event.key === '1' && this.flipped()) {
      this.grade(false);
    } else if (event.key === '2' && this.flipped()) {
      this.grade(true);
    }
  }

  protected restart(): void {
    this.index.set(0);
    this.remembered.set(0);
    this.forgotten.set(0);
    this.failed.set(0);
    this.queue.reload();
  }

  private send(item: ReviewCard, remembered: boolean): void {
    this.reviewApi.review(item.card.id, remembered).subscribe({
      error: (error: unknown) => {
        // 409 = đã chấm thẻ này hôm nay (ví dụ mở 2 tab): không cần báo lỗi.
        if (error instanceof ApiError && error.status === 409) {
          return;
        }
        this.failed.update((n) => n + 1);
        this.toast.error(`Chưa lưu được kết quả thẻ "${item.card.word}".`, {
          label: 'Thử lại',
          run: () => {
            this.failed.update((n) => n - 1);
            this.send(item, remembered);
          },
        });
      },
    });
  }
}
