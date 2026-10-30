import { ChangeDetectionStrategy, Component, effect, inject, input, output, signal } from '@angular/core';
import {
  AbstractControl,
  FormControl,
  NonNullableFormBuilder,
  ReactiveFormsModule,
  ValidationErrors,
  Validators,
} from '@angular/forms';

import { AdminApi } from '../../../core/api/admin.api';
import { AdminQuestion, QUESTION_LIMITS, QuestionRequest } from '../../../core/models/admin.models';
import { ApiError, errorMessage } from '../../../core/models/common.models';
import { OPTION_LETTERS } from '../../../core/util/text';
import { Modal } from '../../../shared/ui/modal/modal';

/**
 * Validator cho CẢ NHÓM 4 đáp án: không được trùng nhau (không phân biệt hoa thường).
 * Cùng luật với Question.validateOptions ở backend, kiểm tra trước để admin thấy lỗi ngay khi gõ.
 */
function distinctOptions(control: AbstractControl): ValidationErrors | null {
  const values = (control.value as string[]).map((v) => v.trim().toLowerCase()).filter(Boolean);
  return new Set(values).size === values.length ? null : { duplicate: true };
}

/**
 * Hộp thoại soạn câu hỏi trắc nghiệm: đề bài, đúng 4 đáp án, chọn 1 đáp án đúng, lời giải thích.
 * question = null: thêm mới vào topicId (có nút "Lưu và soạn câu tiếp").
 */
@Component({
  selector: 'app-question-form-dialog',
  imports: [ReactiveFormsModule, Modal],
  templateUrl: './question-form-dialog.html',
  styleUrl: './question-form-dialog.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class QuestionFormDialog {
  readonly open = input(false);
  readonly topicId = input.required<number>();
  readonly question = input<AdminQuestion | null>(null);
  readonly saved = output<{ question: AdminQuestion; keepOpen: boolean }>();
  readonly closed = output<void>();

  private readonly api = inject(AdminApi);
  private readonly fb = inject(NonNullableFormBuilder);

  protected readonly letters = OPTION_LETTERS;
  protected readonly limits = QUESTION_LIMITS;
  protected readonly saving = signal(false);
  protected readonly serverError = signal<string | null>(null);

  protected readonly form = this.fb.group({
    content: ['', Validators.required],
    // Luôn đúng 4 ô (không thêm / bớt được): khớp luật "đúng 4 đáp án".
    options: this.fb.array<FormControl<string>>(
      Array.from({ length: QUESTION_LIMITS.optionCount }, () => this.optionControl()),
      distinctOptions,
    ),
    // Vị trí đáp án đúng (0..3). null = chưa chọn.
    correctIndex: new FormControl<number | null>(null, Validators.required),
    explanation: ['', Validators.required],
  });

  constructor() {
    effect(() => {
      if (this.open()) {
        this.fill(this.question());
      }
    });
  }

  protected get options() {
    return this.form.controls.options;
  }

  /** Một câu lỗi chung cho cả nhóm đáp án (hiện dưới 4 ô, thay vì lỗi lặp lại dưới từng ô). */
  protected optionsError(correctTouched: boolean): string | null {
    const controls = this.options.controls;
    if (controls.some((c) => c.touched && c.hasError('required'))) {
      return 'Điền đủ cả 4 đáp án';
    }
    if (controls.some((c) => c.hasError('maxlength'))) {
      return `Mỗi đáp án tối đa ${QUESTION_LIMITS.option} ký tự`;
    }
    if (this.options.hasError('duplicate')) {
      return 'Hai đáp án không được trùng nhau';
    }
    if (correctTouched && this.form.controls.correctIndex.invalid) {
      return 'Chọn đáp án đúng bằng ô tròn bên phải';
    }
    return null;
  }

  protected close(): void {
    if (!this.saving()) {
      this.closed.emit();
    }
  }

  protected submit(keepOpen: boolean): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    const v = this.form.getRawValue();
    const request: QuestionRequest = {
      content: v.content.trim(),
      explanation: v.explanation.trim(),
      options: v.options.map((content, i) => ({ content: content.trim(), correct: i === v.correctIndex })),
    };
    const question = this.question();
    this.saving.set(true);
    this.serverError.set(null);
    (question
      ? this.api.updateQuestion(question.questionId, request)
      : this.api.createQuestion(this.topicId(), request)
    ).subscribe({
      next: (result) => {
        this.saving.set(false);
        this.saved.emit({ question: result, keepOpen });
        if (keepOpen) {
          this.fill(null);
        }
      },
      error: (error: unknown) => {
        this.saving.set(false);
        if (error instanceof ApiError && error.fieldErrors['content']) {
          this.form.controls.content.setErrors({ server: error.fieldErrors['content'] });
        } else {
          // 400 INVALID_QUESTION ("Câu hỏi phải có đúng 1 đáp án đúng...") hoặc lỗi khác.
          this.serverError.set(errorMessage(error));
        }
      },
    });
  }

  private fill(question: AdminQuestion | null): void {
    const options = question
      ? [...question.options].sort((a, b) => a.position - b.position)
      : [];
    this.form.reset({
      content: question?.content ?? '',
      options: Array.from({ length: QUESTION_LIMITS.optionCount }, (_, i) => options[i]?.content ?? ''),
      correctIndex: question ? options.findIndex((o) => o.correct) : null,
      explanation: question?.explanation ?? '',
    });
    this.serverError.set(null);
  }

  private optionControl(): FormControl<string> {
    return this.fb.control('', [Validators.required, Validators.maxLength(QUESTION_LIMITS.option)]);
  }
}
