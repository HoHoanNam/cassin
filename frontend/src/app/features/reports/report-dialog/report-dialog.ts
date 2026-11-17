import { ChangeDetectionStrategy, Component, computed, effect, inject, input, output, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { RouterLink } from '@angular/router';

import { ReportApi } from '../../../core/api/report.api';
import { ApiError, errorMessage } from '../../../core/models/common.models';
import { SenseBrief } from '../../../core/models/deck.models';
import { REPORT_REASONS, REPORT_TEXT_MAX, Report, ReportReason } from '../../../core/models/report.models';
import { PART_OF_SPEECH } from '../../../core/util/part-of-speech';
import { Modal } from '../../../shared/ui/modal/modal';

/** Từ cần báo lỗi: chỉ cần cách viết + các nghĩa (WordSummary của bộ từ, LibraryWord của thư viện đều khớp). */
export interface ReportableWord {
  term: string;
  senses: SenseBrief[];
}

/**
 * [THÊM] Báo lỗi một NGHĨA của từ hệ thống (POST /api/reports).
 * Từ có nhiều nghĩa thì chọn nghĩa bị sai; một nghĩa thì chọn sẵn.
 *
 * Lỗi từ server:
 *   409 REPORT_EXISTS   → đã báo nghĩa này, đang chờ xử lý: chỉ đường sang "Báo lỗi của tôi"
 *   400 VALIDATION_ERROR → gắn lỗi vào đúng ô (fieldErrors)
 */
@Component({
  selector: 'app-report-dialog',
  imports: [ReactiveFormsModule, RouterLink, Modal],
  templateUrl: './report-dialog.html',
  styleUrl: './report-dialog.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ReportDialog {
  readonly open = input(false);
  /** Từ hệ thống cần báo lỗi (kèm các nghĩa). */
  readonly word = input<ReportableWord | null>(null);
  readonly closed = output<void>();
  readonly reported = output<Report>();

  private readonly api = inject(ReportApi);
  private readonly fb = inject(FormBuilder).nonNullable;

  protected readonly reasons = REPORT_REASONS;
  protected readonly maxText = REPORT_TEXT_MAX;
  protected readonly pos = PART_OF_SPEECH;
  protected readonly saving = signal(false);
  protected readonly serverError = signal<string | null>(null);
  /** Đã có báo cáo đang chờ cho nghĩa này (409 REPORT_EXISTS) */
  protected readonly alreadyReported = signal(false);

  protected readonly senses = computed(() => this.word()?.senses ?? []);

  protected readonly form = this.fb.group({
    senseId: this.fb.control<number | null>(null, Validators.required),
    reason: this.fb.control<ReportReason | null>(null, Validators.required),
    message: ['', Validators.maxLength(REPORT_TEXT_MAX)],
  });

  constructor() {
    // Mỗi lần mở: làm sạch form, từ chỉ có một nghĩa thì chọn sẵn nghĩa đó.
    effect(() => {
      if (this.open()) {
        const senses = this.senses();
        this.form.reset({ senseId: senses.length === 1 ? senses[0].id : null, reason: null, message: '' });
        this.serverError.set(null);
        this.alreadyReported.set(false);
      }
    });
  }

  protected close(): void {
    if (!this.saving()) {
      this.closed.emit();
    }
  }

  protected submit(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    const { senseId, reason, message } = this.form.getRawValue();
    this.saving.set(true);
    this.serverError.set(null);
    this.alreadyReported.set(false);
    this.api.create({ senseId: senseId!, reason: reason!, message: message.trim() || null }).subscribe({
      next: (report) => {
        this.saving.set(false);
        this.reported.emit(report);
      },
      error: (error: unknown) => {
        this.saving.set(false);
        if (error instanceof ApiError && error.code === 'REPORT_EXISTS') {
          this.alreadyReported.set(true);
          return;
        }
        if (error instanceof ApiError) {
          for (const [field, text] of Object.entries(error.fieldErrors)) {
            this.form.get(field)?.setErrors({ server: text });
          }
        }
        this.serverError.set(errorMessage(error));
      },
    });
  }
}
