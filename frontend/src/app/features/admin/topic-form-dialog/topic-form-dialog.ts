import { ChangeDetectionStrategy, Component, effect, inject, input, output, signal } from '@angular/core';
import { NonNullableFormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';

import { AdminApi } from '../../../core/api/admin.api';
import { TOPIC_LIMITS, TopicRequest } from '../../../core/models/admin.models';
import { ApiError, errorMessage } from '../../../core/models/common.models';
import { CEFR_LEVELS, CefrLevel, TopicDetail } from '../../../core/models/grammar.models';
import { Modal } from '../../../shared/ui/modal/modal';

/** Hộp thoại tạo / sửa chủ đề ngữ pháp. topic = null: tạo mới. */
@Component({
  selector: 'app-topic-form-dialog',
  imports: [ReactiveFormsModule, Modal],
  templateUrl: './topic-form-dialog.html',
  styleUrl: './topic-form-dialog.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class TopicFormDialog {
  readonly open = input(false);
  readonly topic = input<TopicDetail | null>(null);
  readonly saved = output<TopicDetail>();
  readonly closed = output<void>();

  private readonly api = inject(AdminApi);
  private readonly fb = inject(NonNullableFormBuilder);

  protected readonly levels = CEFR_LEVELS;
  protected readonly limits = TOPIC_LIMITS;
  protected readonly saving = signal(false);
  protected readonly serverError = signal<string | null>(null);

  protected readonly form = this.fb.group({
    title: ['', [Validators.required, Validators.maxLength(TOPIC_LIMITS.title)]],
    level: this.fb.control<CefrLevel>('A2', Validators.required),
    explanation: ['', Validators.required],
  });

  constructor() {
    effect(() => {
      if (this.open()) {
        const topic = this.topic();
        this.form.reset({
          title: topic?.title ?? '',
          level: topic?.level ?? 'A2',
          explanation: topic?.explanation ?? '',
        });
        this.serverError.set(null);
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
    const v = this.form.getRawValue();
    // Giữ nguyên xuống dòng trong bài giải thích (định dạng đoạn / gạch đầu dòng), chỉ cắt khoảng trắng 2 đầu.
    const request: TopicRequest = { title: v.title.trim(), level: v.level, explanation: v.explanation.trim() };
    const topic = this.topic();
    this.saving.set(true);
    this.serverError.set(null);
    (topic ? this.api.updateTopic(topic.id, request) : this.api.createTopic(request)).subscribe({
      next: (result) => {
        this.saving.set(false);
        this.saved.emit(result);
      },
      error: (error: unknown) => {
        this.saving.set(false);
        if (error instanceof ApiError && Object.keys(error.fieldErrors).length > 0) {
          for (const [field, message] of Object.entries(error.fieldErrors)) {
            this.form.get(field)?.setErrors({ server: message });
          }
        } else {
          // 400 INVALID_TOPIC (luật của entity) hoặc lỗi khác: hiện câu thông báo của backend.
          this.serverError.set(errorMessage(error));
        }
      },
    });
  }
}
