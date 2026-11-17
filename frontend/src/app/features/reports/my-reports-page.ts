import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { rxResource } from '@angular/core/rxjs-interop';
import { RouterLink } from '@angular/router';

import { ReportApi } from '../../core/api/report.api';
import { errorMessage } from '../../core/models/common.models';
import { REPORT_REASON_LABEL, REPORT_STATUS_LABEL, Report } from '../../core/models/report.models';
import { ToastService } from '../../core/ui/toast.service';
import { relativeDay } from '../../core/util/dates';
import { ConfirmDialog } from '../../shared/ui/confirm-dialog/confirm-dialog';
import { EmptyState } from '../../shared/ui/empty-state/empty-state';
import { ErrorState } from '../../shared/ui/error-state/error-state';
import { Pager } from '../../shared/ui/pager/pager';
import { Skeleton } from '../../shared/ui/skeleton/skeleton';

const PAGE_SIZE = 10;

/**
 * [THÊM] Báo lỗi của tôi (GET /api/reports): mới nhất trước, có trạng thái và phản hồi của quản trị viên.
 * Báo cáo đang chờ thì rút lại được (DELETE /api/reports/{id}); đã xử lý thì giữ làm lịch sử.
 */
@Component({
  selector: 'app-my-reports-page',
  imports: [RouterLink, ConfirmDialog, EmptyState, ErrorState, Pager, Skeleton],
  templateUrl: './my-reports-page.html',
  styleUrl: './my-reports-page.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class MyReportsPage {
  private readonly api = inject(ReportApi);
  private readonly toast = inject(ToastService);

  protected readonly page = signal(0);
  protected readonly reasonLabel = REPORT_REASON_LABEL;
  protected readonly statusLabel = REPORT_STATUS_LABEL;
  protected readonly relativeDay = relativeDay;

  protected readonly reports = rxResource({
    params: () => this.page(),
    stream: ({ params }) => this.api.listMine(params, PAGE_SIZE),
  });

  protected readonly toWithdraw = signal<Report | null>(null);
  protected readonly busy = signal(false);

  protected withdraw(): void {
    const report = this.toWithdraw();
    if (!report) {
      return;
    }
    this.busy.set(true);
    this.api.withdraw(report.id).subscribe({
      next: () => {
        this.busy.set(false);
        this.toWithdraw.set(null);
        this.toast.success(`Đã rút báo lỗi "${report.term}"`);
        // Rút báo cáo cuối cùng của trang cuối → lùi về trang trước.
        if (this.reports.hasValue() && this.reports.value().content.length === 1 && this.page() > 0) {
          this.page.update((p) => p - 1);
        } else {
          this.reports.reload();
        }
      },
      error: (error: unknown) => {
        this.busy.set(false);
        this.toWithdraw.set(null);
        this.toast.error(errorMessage(error));
        this.reports.reload(); // vd 409 REPORT_CLOSED: admin vừa xử lý xong → hiện trạng thái mới
      },
    });
  }
}
