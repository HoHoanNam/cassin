import { ChangeDetectionStrategy, Component, computed, input, output } from '@angular/core';

import {
  BLANK_MARKER,
  HintView,
  PASSAGE_LIMITS,
  QuestionView,
  REJECTED_REASON_LABEL,
  TFNG_OPTIONS,
  TfngAnswer,
} from '../../../core/models/passage.models';
import { checkBlankAnswer, parseInstruction } from '../../../core/util/passage-text';
import { Icon } from '../../../shared/ui/icon/icon';

/** Câu trả lời đang làm của MỘT câu: mỗi dạng dùng một field, giống AnswerRequest. */
export interface DraftAnswer {
  optionId?: number | null;
  tfngAnswer?: TfngAnswer | null;
  text?: string | null;
}

/**
 * [THÊM] Một câu hỏi khi đang làm bài, đủ 3 dạng:
 *   MCQ   : 4 lựa chọn A–D
 *   TFNG  : 3 nút True / False / Not Given
 *   BLANK : ô nhập nằm ngay chỗ "____" trong câu, kèm dòng hướng dẫn số từ
 * Component "ngốc": chỉ hiện và báo sự kiện lên trang cha, không gọi API.
 */
@Component({
  selector: 'app-reading-question',
  imports: [Icon],
  templateUrl: './reading-question.html',
  styleUrl: './reading-question.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ReadingQuestion {
  readonly question = input.required<QuestionView>();
  readonly answer = input<DraftAnswer | undefined>(undefined);
  readonly hints = input<HintView[]>([]);
  readonly hintsRemaining = input(0);
  readonly hintBusy = input(false);
  readonly answerChange = output<DraftAnswer>();
  readonly askHint = output<void>();

  protected readonly tfngOptions = TFNG_OPTIONS;
  protected readonly maxText = PASSAGE_LIMITS.answerText;

  /** "Solar power is a cheap replacement for ____." → ["Solar power is a cheap replacement for ", "."] */
  protected readonly blankParts = computed(() => {
    const [before, ...rest] = this.question().prompt.split(BLANK_MARKER);
    return { before, after: rest.join(BLANK_MARKER) };
  });

  /** Cảnh báo trước khi nộp nếu câu điền từ sai giới hạn (backend vẫn là nơi chấm). */
  protected readonly blankWarning = computed(() => {
    const text = this.answer()?.text?.trim();
    const rule = parseInstruction(this.question().instruction);
    if (!text || !rule) {
      return null;
    }
    const reason = checkBlankAnswer(rule.kind, rule.maxWords, text);
    return reason ? REJECTED_REASON_LABEL[reason] : null;
  });

  protected readonly totalHints = computed(() => this.hints().length + this.hintsRemaining());

  protected chooseOption(optionId: number): void {
    this.answerChange.emit({ optionId });
  }

  protected chooseTfng(value: TfngAnswer): void {
    this.answerChange.emit({ tfngAnswer: value });
  }

  protected typeText(event: Event): void {
    this.answerChange.emit({ text: (event.target as HTMLInputElement).value });
  }
}
