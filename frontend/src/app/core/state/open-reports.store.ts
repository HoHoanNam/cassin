import { Injectable, inject } from '@angular/core';
import { rxResource } from '@angular/core/rxjs-interop';

import { ReportApi } from '../api/report.api';

/**
 * [THÊM] Số báo lỗi đang chờ xử lý (GET /api/admin/reports/open-count), cho huy hiệu trên tab quản trị.
 * Chỉ tải khi có nơi inject (khu vực quản trị), học viên không bao giờ gọi API admin này.
 * Gọi refresh() sau khi xử lý một báo cáo để huy hiệu cập nhật.
 */
@Injectable({ providedIn: 'root' })
export class OpenReportsStore {
  private readonly api = inject(ReportApi);

  readonly count = rxResource({ stream: () => this.api.openCount() });

  /** 0 khi đang tải hoặc lỗi (huy hiệu chỉ là phụ) */
  value(): number {
    return this.count.hasValue() ? this.count.value() : 0;
  }

  refresh(): void {
    this.count.reload();
  }
}
