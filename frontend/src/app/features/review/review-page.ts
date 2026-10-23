import { ChangeDetectionStrategy, Component, computed, inject, input, signal } from '@angular/core';
import { rxResource } from '@angular/core/rxjs-interop';
import { RouterLink } from '@angular/router';

import { FlashCard } from './flash-card/flash-card';
import { ReviewOutcome } from './review.models';
import { ReviewService } from './review.service';

/**
 * Buổi ôn flashcard: xem từ → lật thẻ → tự đánh giá Nhớ / Quên → thẻ tiếp theo.
 * Dùng cho cả /review (ôn tất cả) và /decks/:id/review (ôn một bộ).
 */
@Component({
  selector: 'app-review-page',
  imports: [RouterLink, FlashCard],
  templateUrl: './review-page.html',
  styleUrl: './review-page.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
  // Nghe phím trên toàn trang để có phím tắt.
  host: { '(document:keydown)': 'onKeydown($event)' },
})
export class ReviewPage {
  private readonly reviewService = inject(ReviewService);

  /** :id trên URL. Không có (trang /review) thì undefined → ôn tất cả các bộ. */
  readonly id = input<string>();

  /**
   * rxResource: tải dữ liệu theo params. Khi id() đổi, nó tự tải lại.
   * Cung cấp sẵn value(), isLoading(), error() dưới dạng signal.
   */
  protected readonly session = rxResource({
    params: () => ({ deckId: this.id() ? Number(this.id()) : null }),
    stream: ({ params }) => this.reviewService.getSession(params.deckId),
  });

  protected readonly cards = computed(() => this.session.value() ?? []);
  protected readonly index = signal(0);
  protected readonly flipped = signal(false);
  protected readonly answering = signal(false);
  protected readonly outcomes = signal<ReviewOutcome[]>([]);

  protected readonly current = computed(() => this.cards()[this.index()]);
  protected readonly total = computed(() => this.cards().length);
  protected readonly finished = computed(() => this.total() > 0 && this.index() >= this.total());
  protected readonly progressPercent = computed(() =>
    this.total() === 0 ? 0 : (this.index() / this.total()) * 100,
  );
  protected readonly rememberedCount = computed(
    () => this.outcomes().filter((o) => o.remembered).length,
  );

  protected toggleFlip(): void {
    if (this.current() && !this.answering()) {
      this.flipped.update((value) => !value);
    }
  }

  protected answer(remembered: boolean): void {
    const card = this.current();
    // Chỉ chấm được sau khi đã lật thẻ, và không chấm 2 lần khi đang chờ server.
    if (!card || !this.flipped() || this.answering()) {
      return;
    }
    this.answering.set(true);
    this.reviewService.answer(card.cardId, card.box, remembered).subscribe(({ toBox }) => {
      this.outcomes.update((list) => [
        ...list,
        { cardId: card.cardId, word: card.word, remembered, fromBox: card.box, toBox },
      ]);
      this.flipped.set(false);
      this.index.update((i) => i + 1);
      this.answering.set(false);
    });
  }

  /** Phím cách: lật thẻ. Sau khi lật: phím 1 = Quên, phím 2 = Nhớ. */
  protected onKeydown(event: KeyboardEvent): void {
    if (this.finished() || event.repeat) {
      return;
    }
    if (event.code === 'Space') {
      // Chặn trang cuộn xuống khi nhấn phím cách.
      event.preventDefault();
      this.toggleFlip();
    } else if (event.key === '1') {
      this.answer(false);
    } else if (event.key === '2') {
      this.answer(true);
    }
  }
}
