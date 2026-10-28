import { ChangeDetectionStrategy, Component, computed, inject, input, numberAttribute, signal } from '@angular/core';
import { rxResource } from '@angular/core/rxjs-interop';
import { RouterLink } from '@angular/router';

import { QuizApi } from '../../core/api/quiz.api';
import { ApiError } from '../../core/models/common.models';
import { relativeDay } from '../../core/util/dates';
import { OPTION_LETTERS } from '../../core/util/text';
import { EmptyState } from '../../shared/ui/empty-state/empty-state';
import { ErrorState } from '../../shared/ui/error-state/error-state';
import { Icon } from '../../shared/ui/icon/icon';
import { ScoreStamp } from '../../shared/ui/score-stamp/score-stamp';
import { Skeleton } from '../../shared/ui/skeleton/skeleton';

/**
 * Bài đã chấm, trình bày như cô giáo chấm bằng bút đỏ:
 * khoanh đáp án đúng, gạch đáp án sai đã chọn, ghi lời giải thích bên dưới.
 */
@Component({
  selector: 'app-quiz-result-page',
  imports: [RouterLink, EmptyState, ErrorState, Icon, ScoreStamp, Skeleton],
  templateUrl: './quiz-result-page.html',
  styleUrl: './quiz-result-page.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class QuizResultPage {
  readonly attemptId = input.required({ transform: numberAttribute });

  private readonly api = inject(QuizApi);

  protected readonly letters = OPTION_LETTERS;
  protected readonly relativeDay = relativeDay;
  protected readonly onlyWrong = signal(false);

  protected readonly result = rxResource({
    params: () => this.attemptId(),
    stream: ({ params }) => this.api.result(params),
  });

  protected readonly notFound = computed(() => {
    const error = this.result.error();
    return error instanceof ApiError && (error.status === 404 || error.code === 'QUIZ_NOT_SUBMITTED');
  });

  protected readonly visibleAnswers = computed(() => {
    if (!this.result.hasValue()) {
      return [];
    }
    const answers = this.result.value().answers;
    return this.onlyWrong() ? answers.filter((a) => !a.correct) : answers;
  });

  protected readonly wrongCount = computed(() =>
    this.result.hasValue() ? this.result.value().answers.filter((a) => !a.correct).length : 0,
  );

  /** Lời phê theo tỉ lệ đúng. */
  protected readonly remark = computed(() => {
    if (!this.result.hasValue()) {
      return '';
    }
    const { score, total } = this.result.value();
    const ratio = total ? score / total : 0;
    if (ratio === 1) return 'Xuất sắc! Không sai câu nào.';
    if (ratio >= 0.8) return 'Làm tốt, cố gắng thêm nhé.';
    if (ratio >= 0.5) return 'Khá rồi. Xem lại các câu sai bên dưới.';
    return 'Nên đọc lại phần lý thuyết rồi làm lại.';
  });
}
