import { ChangeDetectionStrategy, Component, input, output } from '@angular/core';

import { BoxMeter } from '../../../shared/box-meter/box-meter';
import { PosLabelPipe } from '../../../shared/pos-label.pipe';
import { ReviewCard } from '../review.models';

/**
 * Một tấm thẻ học hai mặt. Chỉ hiển thị; việc lật do component cha quyết định:
 * cha truyền [flipped], thẻ chỉ báo lên (flip) khi người dùng bấm vào.
 */
@Component({
  selector: 'app-flash-card',
  imports: [BoxMeter, PosLabelPipe],
  templateUrl: './flash-card.html',
  styleUrl: './flash-card.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class FlashCard {
  readonly card = input.required<ReviewCard>();
  readonly flipped = input(false);

  // output(): sự kiện gửi lên cha, cha nghe bằng (flip)="..."
  readonly flip = output<void>();
}
