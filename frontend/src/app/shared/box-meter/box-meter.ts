import { ChangeDetectionStrategy, Component, input } from '@angular/core';

/**
 * 5 ô vuông nhỏ cho biết thẻ đang ở hộp Leitner nào.
 * Hộp 3 → 3 ô tô màu. box = null → thẻ chưa ôn lần nào.
 */
@Component({
  selector: 'app-box-meter',
  templateUrl: './box-meter.html',
  styleUrl: './box-meter.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class BoxMeter {
  readonly box = input.required<number | null>();

  protected readonly slots = [1, 2, 3, 4, 5];
}
