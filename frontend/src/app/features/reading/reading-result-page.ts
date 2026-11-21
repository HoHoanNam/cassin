import { ChangeDetectionStrategy, Component, computed, inject, input, numberAttribute, signal } from '@angular/core';
import { rxResource } from '@angular/core/rxjs-interop';
import { RouterLink } from '@angular/router';

import { PassageApi } from '../../core/api/passage.api';
import { ApiError } from '../../core/models/common.models';
import { QuestionResult, REJECTED_REASON_LABEL, TFNG_LABEL } from '../../core/models/passage.models';
import { relativeDay } from '../../core/util/dates';
import { Mark } from '../../core/util/passage-text';
import { EmptyState } from '../../shared/ui/empty-state/empty-state';
import { ErrorState } from '../../shared/ui/error-state/error-state';
import { Icon } from '../../shared/ui/icon/icon';
import { ScoreStamp } from '../../shared/ui/score-stamp/score-stamp';
import { Skeleton } from '../../shared/ui/skeleton/skeleton';
import { PassageText } from './passage-text/passage-text';

/**
 * [THÊM] Kết quả bài đọc. Mỗi câu: đáp án của bạn, đáp án đúng, lời giải thích, gợi ý đồng nghĩa.
 * Bấm "Xem trong bài" → câu bằng chứng được tô xanh trong bài đọc để thấy VÌ SAO đáp án đúng.
 */
@Component({
  selector: 'app-reading-result-page',
  imports: [RouterLink, EmptyState, ErrorState, Icon, PassageText, ScoreStamp, Skeleton],
  templateUrl: './reading-result-page.html',
  styleUrl: './reading-result-page.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ReadingResultPage {
  readonly attemptId = input.required({ transform: numberAttribute });

  private readonly api = inject(PassageApi);

  protected readonly relativeDay = relativeDay;
  protected readonly tfngLabel = TFNG_LABEL;
  protected readonly rejectedLabel = REJECTED_REASON_LABEL;
  protected readonly onlyWrong = signal(false);
  /** Câu đang được soi bằng chứng trong bài */
  protected readonly focused = signal<QuestionResult | null>(null);
  protected readonly scrollKey = signal<number | null>(null);

  protected readonly result = rxResource({
    params: () => this.attemptId(),
    stream: ({ params }) => this.api.result(params),
  });

  protected readonly notFound = computed(() => {
    const error = this.result.error();
    return error instanceof ApiError && (error.status === 404 || error.code === 'ATTEMPT_NOT_SUBMITTED');
  });

  protected readonly visible = computed(() => {
    const questions = this.result.hasValue() ? this.result.value().questions : [];
    return this.onlyWrong() ? questions.filter((q) => !q.correct) : questions;
  });

  protected readonly wrongCount = computed(() =>
    this.result.hasValue() ? this.result.value().questions.filter((q) => !q.correct).length : 0,
  );

  /** Câu đang soi: tô xanh câu bằng chứng + tô vàng các cụm đồng nghĩa của câu đó. */
  protected readonly marks = computed<Mark[]>(() => {
    const q = this.focused();
    return q
      ? [{ text: q.evidence, kind: 'evidence' }, ...q.hints.map((h) => ({ text: h.passagePhrase, kind: 'hint' as const }))]
      : [];
  });

  protected readonly remark = computed(() => {
    if (!this.result.hasValue()) {
      return '';
    }
    const { score, total, correctWithoutHints } = this.result.value();
    const ratio = total ? score / total : 0;
    if (ratio === 1 && correctWithoutHints === total) return 'Xuất sắc! Đúng hết, không cần gợi ý.';
    if (ratio === 1) return 'Đúng hết! Lần sau thử không dùng gợi ý nhé.';
    if (ratio >= 0.6) return 'Khá tốt. Soi lại câu bằng chứng ở các câu sai.';
    return 'Đọc lại bài, chú ý các từ đồng nghĩa.';
  });

  protected show(question: QuestionResult): void {
    this.focused.set(question);
    this.scrollKey.update((k) => (k ?? 0) + 1);
  }

  /** "Bạn chọn B · Đáp án đúng C" cho từng dạng câu */
  protected yourAnswer(q: QuestionResult): string | null {
    switch (q.type) {
      case 'MCQ': {
        const option = q.options?.find((o) => o.id === q.selectedOptionId);
        return option ? `${option.label}. ${option.content}` : null;
      }
      case 'TFNG':
        return q.yourTfngAnswer ? TFNG_LABEL[q.yourTfngAnswer] : null;
      case 'BLANK':
        return q.yourText ?? null;
    }
  }

  protected correctAnswer(q: QuestionResult): string {
    switch (q.type) {
      case 'MCQ': {
        const option = q.options?.find((o) => o.correct);
        return option ? `${option.label}. ${option.content}` : '';
      }
      case 'TFNG':
        return q.correctTfngAnswer ? TFNG_LABEL[q.correctTfngAnswer] : '';
      case 'BLANK':
        return (q.acceptedAnswers ?? []).join(' / ');
    }
  }
}
