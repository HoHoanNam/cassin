import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';
import { RouterLink } from '@angular/router';

import { ReviewSummary } from '../home.models';

/** Số ngày ôn lại theo hộp, khớp với CardProgress.intervalDays() ở backend. */
const INTERVAL_LABELS = ['1 ngày', '2 ngày', '4 ngày', '7 ngày', '15 ngày'];

@Component({
  selector: 'app-review-panel',
  imports: [RouterLink],
  templateUrl: './review-panel.html',
  styleUrl: './review-panel.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ReviewPanel {
  // input.required: component cha bắt buộc phải truyền [review]="...".
  readonly review = input.required<ReviewSummary>();

  // computed: tự tính lại khi review() thay đổi.
  protected readonly boxes = computed(() => {
    const counts = this.review().boxCounts;
    const max = Math.max(...counts, 1);
    return counts.map((count, index) => ({
      number: index + 1,
      count,
      interval: INTERVAL_LABELS[index],
      // Chiều cao cột theo tỉ lệ với hộp nhiều thẻ nhất, tối thiểu 4% để cột rỗng vẫn thấy được.
      heightPercent: Math.max((count / max) * 100, 4),
      mastered: index === counts.length - 1,
    }));
  });

  protected readonly totalCards = computed(() =>
    this.review().boxCounts.reduce((sum, count) => sum + count, 0),
  );
}
