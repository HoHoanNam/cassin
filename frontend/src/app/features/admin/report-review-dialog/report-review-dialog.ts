import { ChangeDetectionStrategy, Component, computed, effect, inject, input, output, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';

import { ReportApi } from '../../../core/api/report.api';
import { ApiError, errorMessage } from '../../../core/models/common.models';
import {
  AdminReport,
  REPORT_REASON_LABEL,
  REPORT_TEXT_MAX,
  ReviewReportRequest,
} from '../../../core/models/report.models';
import { Modal } from '../../../shared/ui/modal/modal';

type Decision = ReviewReportRequest['status'];

/**
 * [THÊM] Admin xử lý một báo lỗi (PATCH /api/admin/reports/{id}).
 *   "Đã sửa"  (RESOLVED): ghi chú không bắt buộc
 *   "Từ chối" (REJECTED): BẮT BUỘC ghi lý do, để người báo hiểu vì sao (backend: 400 NOTE_REQUIRED)
 * 409 REPORT_CLOSED: admin khác vừa xử lý trước → báo và cho trang tải lại.
 */
@Component({
  selector: 'app-report-review-dialog',
  imports: [ReactiveFormsModule, Modal],
  templateUrl: './report-review-dialog.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ReportReviewDialog {
  readonly open = input(false);
  readonly report = input<AdminReport | null>(null);
  /** Hướng xử lý chọn sẵn theo nút admin vừa bấm */
  readonly initialDecision = input<Decision>('RESOLVED');
  readonly closed = output<void>();
  readonly reviewed = output<AdminReport>();
  /** Báo cáo đã bị người khác xử lý: trang cha nên tải lại danh sách */
  readonly stale = output<void>();

  private readonly api = inject(ReportApi);
  private readonly fb = inject(FormBuilder).nonNullable;

  protected readonly reasonLabel = REPORT_REASON_LABEL;
  protected readonly maxText = REPORT_TEXT_MAX;
  protected readonly saving = signal(false);
  protected readonly serverError = signal<string | null>(null);

  protected readonly form = this.fb.group({
    status: this.fb.control<Decision>('RESOLVED'),
    adminNote: ['', Validators.maxLength(REPORT_TEXT_MAX)],
  });

  private readonly decision = signal<Decision>('RESOLVED');
  protected readonly rejecting = computed(() => this.decision() === 'REJECTED');

  constructor() {
    effect(() => {
      if (this.open()) {
        this.form.reset({ status: this.initialDecision(), adminNote: '' });
        this.decision.set(this.initialDecision());
        this.serverError.set(null);
      }
    });
    this.form.controls.status.valueChanges.pipe(takeUntilDestroyed()).subscribe((value) => this.decision.set(value));
  }

  protected close(): void {
    if (!this.saving()) {
      this.closed.emit();
    }
  }

  protected submit(): void {
    const report = this.report();
    const { status, adminNote } = this.form.getRawValue();
    const note = adminNote.trim();
    if (status === 'REJECTED' && !note) {
      this.form.controls.adminNote.setErrors({ required: true });
      this.form.controls.adminNote.markAsTouched();
      return;
    }
    if (!report || this.form.invalid) {
      return;
    }
    this.saving.set(true);
    this.serverError.set(null);
    this.api.review(report.id, { status, adminNote: note || null }).subscribe({
      next: (updated) => {
        this.saving.set(false);
        this.reviewed.emit(updated);
      },
      error: (error: unknown) => {
        this.saving.set(false);
        if (error instanceof ApiError && error.code === 'NOTE_REQUIRED') {
          this.form.controls.adminNote.setErrors({ server: error.message });
          return;
        }
        if (error instanceof ApiError && error.code === 'REPORT_CLOSED') {
          this.stale.emit();
        }
        this.serverError.set(errorMessage(error));
      },
    });
  }
}
