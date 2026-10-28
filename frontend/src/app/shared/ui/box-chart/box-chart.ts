import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';

import { BOX_INTERVAL_DAYS, BoxCount } from '../../../core/models/review.models';

/**
 * Biểu đồ 5 hộp Leitner: mỗi cột là số thẻ trong một hộp. Hộp 5 (đã thuộc) tô đậm.
 * Đọc được bằng trình đọc màn hình nhờ aria-label tóm tắt toàn bộ số liệu.
 */
@Component({
  selector: 'app-box-chart',
  templateUrl: './box-chart.html',
  styleUrl: './box-chart.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class BoxChart {
  readonly boxes = input.required<BoxCount[]>();
  /** compact: bản nhỏ cho điện thoại, không có nhãn số ngày */
  readonly compact = input(false);

  protected readonly intervals = BOX_INTERVAL_DAYS;

  protected readonly total = computed(() => this.boxes().reduce((sum, b) => sum + b.total, 0));

  protected readonly bars = computed(() => {
    const max = Math.max(1, ...this.boxes().map((b) => b.total));
    // Cột cao nhất chiếm 80% khung (chừa chỗ cho con số phía trên); cột thấp nhất vẫn cao 6% để thấy được hộp trống.
    return this.boxes().map((b) => ({ ...b, height: Math.max(6, Math.round((b.total / max) * 80)) }));
  });

  protected readonly summary = computed(
    () => 'Số thẻ theo hộp: ' + this.boxes().map((b) => `hộp ${b.box} có ${b.total} thẻ`).join(', '),
  );
}
