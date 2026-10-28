import { ChangeDetectionStrategy, Component, computed, input, output } from '@angular/core';

import { ReviewCard } from '../../../core/models/review.models';
import { PART_OF_SPEECH } from '../../../core/util/part-of-speech';

/**
 * Thẻ học kẻ dòng. Mặt trước: từ + phiên âm. Mặt sau: nghĩa + câu ví dụ.
 * Cả thẻ là MỘT nút bấm để lật (bàn phím và trình đọc màn hình dùng được).
 */
@Component({
  selector: 'app-flash-card',
  templateUrl: './flash-card.html',
  styleUrl: './flash-card.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class FlashCard {
  readonly item = input.required<ReviewCard>();
  readonly flipped = input(false);
  readonly flip = output<void>();

  protected readonly posLabel = computed(() => PART_OF_SPEECH[this.item().card.partOfSpeech].label);
  protected readonly label = computed(() =>
    this.flipped()
      ? `Nghĩa: ${this.item().card.definition}`
      : `Từ: ${this.item().card.word}. Bấm để lật thẻ`,
  );
}
