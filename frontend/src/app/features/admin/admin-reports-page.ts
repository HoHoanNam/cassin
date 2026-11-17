import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { rxResource } from '@angular/core/rxjs-interop';

import { ReportApi } from '../../core/api/report.api';
import {
  AdminReport,
  REPORT_REASON_LABEL,
  REPORT_STATUS_LABEL,
  ReportStatus,
  ReviewReportRequest,
} from '../../core/models/report.models';
import { OpenReportsStore } from '../../core/state/open-reports.store';
import { ToastService } from '../../core/ui/toast.service';
import { relativeDay } from '../../core/util/dates';
import { EmptyState } from '../../shared/ui/empty-state/empty-state';
import { ErrorState } from '../../shared/ui/error-state/error-state';
import { Pager } from '../../shared/ui/pager/pager';
import { Skeleton } from '../../shared/ui/skeleton/skeleton';
import { ReportReviewDialog } from './report-review-dialog/report-review-dialog';

const PAGE_SIZE = 10;

type Filter = ReportStatus | 'ALL';

/**
 * [THÊM] Admin duyệt báo lỗi nội dung (GET /api/admin/reports?status=). Mặc định: đang chờ, mới nhất trước.
 */
@Component({
  selector: 'app-admin-reports-page',
  imports: [EmptyState, ErrorState, Pager, ReportReviewDialog, Skeleton],
  templateUrl: './admin-reports-page.html',
  styleUrl: './admin-reports-page.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class AdminReportsPage {
  private readonly api = inject(ReportApi);
  private readonly toast = inject(ToastService);
  private readonly openReports = inject(OpenReportsStore);

  protected readonly filters: { value: Filter; label: string }[] = [
    { value: 'OPEN', label: 'Đang chờ' },
    { value: 'RESOLVED', label: 'Đã sửa' },
    { value: 'REJECTED', label: 'Không sửa' },
    { value: 'ALL', label: 'Tất cả' },
  ];
  protected readonly reasonLabel = REPORT_REASON_LABEL;
  protected readonly statusLabel = REPORT_STATUS_LABEL;
  protected readonly relativeDay = relativeDay;

  protected readonly filter = signal<Filter>('OPEN');
  protected readonly page = signal(0);
  protected readonly reports = rxResource({
    params: () => ({ status: this.filter(), page: this.page() }),
    stream: ({ params }) =>
      this.api.adminList(params.status === 'ALL' ? null : params.status, params.page, PAGE_SIZE),
  });

  protected readonly reviewing = signal<AdminReport | null>(null);
  protected readonly decision = signal<ReviewReportRequest['status']>('RESOLVED');

  protected setFilter(value: Filter): void {
    this.filter.set(value);
    this.page.set(0);
  }

  protected openReview(report: AdminReport, decision: ReviewReportRequest['status']): void {
    this.decision.set(decision);
    this.reviewing.set(report);
  }

  protected onReviewed(report: AdminReport): void {
    this.reviewing.set(null);
    this.toast.success(report.status === 'RESOLVED' ? `Đã đánh dấu sửa "${report.term}"` : `Đã từ chối báo lỗi "${report.term}"`);
    this.afterChange();
  }

  /** Admin khác vừa xử lý trước: đóng hộp thoại, tải lại để thấy trạng thái mới. */
  protected onStale(): void {
    this.reviewing.set(null);
    this.toast.info('Báo lỗi này vừa được xử lý ở nơi khác');
    this.afterChange();
  }

  private afterChange(): void {
    this.openReports.refresh();
    // Đang lọc "Đang chờ" mà xử lý mục cuối của trang cuối → lùi về trang trước.
    if (this.filter() === 'OPEN' && this.reports.hasValue() && this.reports.value().content.length === 1 && this.page() > 0) {
      this.page.update((p) => p - 1);
    } else {
      this.reports.reload();
    }
  }
}
