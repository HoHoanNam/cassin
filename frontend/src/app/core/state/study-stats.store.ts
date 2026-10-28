import { Injectable, inject } from '@angular/core';
import { rxResource } from '@angular/core/rxjs-interop';

import { ReviewApi } from '../api/review.api';

/**
 * Thống kê ôn tập dùng chung: số thẻ đến hạn (badge trên menu), số thẻ mỗi hộp, chuỗi ngày học.
 * Nhiều nơi cùng đọc (sidebar, thanh dưới đáy, trang Hôm nay) nên đặt ở MỘT service,
 * tránh mỗi nơi tự gọi API một lần.
 *
 * Gọi refresh() sau khi ôn xong một buổi để số liệu cập nhật.
 */
@Injectable({ providedIn: 'root' })
export class StudyStatsStore {
  private readonly api = inject(ReviewApi);

  readonly stats = rxResource({ stream: () => this.api.stats() });

  /** Số thẻ đến hạn; 0 khi đang tải hoặc lỗi (badge chỉ là phụ, không cần báo lỗi). */
  dueCount(): number {
    return this.stats.hasValue() ? this.stats.value().dueToday : 0;
  }

  refresh(): void {
    this.stats.reload();
  }
}
