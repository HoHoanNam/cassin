import { ChangeDetectionStrategy, Component, computed, inject, input, output } from '@angular/core';
import { toObservable, toSignal } from '@angular/core/rxjs-interop';
import { NonNullableFormBuilder, ReactiveFormsModule } from '@angular/forms';
import { startWith, switchMap } from 'rxjs';

import {
  ANSWER_KIND_OPTIONS,
  BLANK_MARKER,
  PASSAGE_LIMITS,
  QUESTION_TYPE_LABEL,
  QuestionType,
  TFNG_OPTIONS,
  TfngAnswer,
} from '../../../core/models/passage.models';
import { OPTION_LETTERS } from '../../../core/util/text';
import { blankInstruction } from '../../../core/util/passage-text';
import { Icon } from '../../../shared/ui/icon/icon';
import { QuestionForm, createHintForm } from './passage-form';

/**
 * [THÊM] Soạn MỘT câu hỏi trong trang soạn bài đọc. Đổi được dạng câu bất kỳ lúc nào
 * (form có sẵn field của cả 3 dạng). Câu bằng chứng CHỌN từ danh sách câu của bài, không gõ tay.
 */
@Component({
  selector: 'app-passage-question-editor',
  imports: [ReactiveFormsModule, Icon],
  templateUrl: './passage-question-editor.html',
  styleUrl: './passage-question-editor.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class PassageQuestionEditor {
  readonly form = input.required<QuestionForm>();
  readonly index = input.required<number>();
  readonly total = input.required<number>();
  /** Các câu của bài đọc (để chọn câu bằng chứng) */
  readonly sentences = input.required<string[]>();
  readonly remove = output<void>();
  readonly move = output<-1 | 1>();

  private readonly fb = inject(NonNullableFormBuilder);

  protected readonly types = Object.entries(QUESTION_TYPE_LABEL) as [QuestionType, string][];
  protected readonly tfngOptions = TFNG_OPTIONS;
  protected readonly kindOptions = ANSWER_KIND_OPTIONS;
  protected readonly letters = OPTION_LETTERS;
  protected readonly marker = BLANK_MARKER;
  protected readonly maxHints = PASSAGE_LIMITS.hints;

  /** Giá trị form dưới dạng signal, để template tính lại khi admin gõ. */
  private readonly value = toSignal(
    toObservable(this.form).pipe(switchMap((form) => form.valueChanges.pipe(startWith(form.getRawValue())))),
  );

  protected readonly type = computed(() => this.value()?.type ?? 'MCQ');
  protected readonly instruction = computed(() => {
    const v = this.value();
    return v?.answerKind ? blankInstruction(v.answerKind, v.answerKind === 'NUMBER' ? null : (v.maxWords ?? 1)) : '';
  });
  /** Câu bằng chứng đã lưu nhưng không còn trong bài (admin vừa sửa nội dung) */
  protected readonly staleEvidence = computed(() => {
    const evidence = this.value()?.evidence ?? '';
    return evidence && !this.sentences().includes(evidence) ? evidence : null;
  });

  protected setCorrect(i: number): void {
    const control = this.form().controls.correctIndex;
    control.setValue(i);
    control.markAsDirty();
  }

  protected setTfng(value: TfngAnswer): void {
    const control = this.form().controls.tfngAnswer;
    control.setValue(value);
    control.markAsDirty();
  }

  protected addHint(): void {
    this.form().controls.hints.push(createHintForm(this.fb));
  }

  protected removeHint(i: number): void {
    this.form().controls.hints.removeAt(i);
  }

  /** Đổ sẵn "____" vào cuối đề khi chuyển sang dạng điền từ mà đề chưa có chỗ trống. */
  protected insertBlank(): void {
    const prompt = this.form().controls.prompt;
    if (!prompt.value.includes(BLANK_MARKER)) {
      prompt.setValue(`${prompt.value.trimEnd()} ${BLANK_MARKER}`.trim());
    }
  }
}
