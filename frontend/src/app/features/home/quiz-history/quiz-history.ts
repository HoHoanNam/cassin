import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';

import { relativeDay } from '../../../shared/vn-date';
import { QuizAttemptSummary } from '../home.models';

@Component({
  selector: 'app-quiz-history',
  templateUrl: './quiz-history.html',
  styleUrl: './quiz-history.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class QuizHistory {
  readonly quizzes = input.required<QuizAttemptSummary[]>();

  // Tính sẵn chuỗi ngày ở đây thay vì gọi hàm trong template:
  // hàm gọi trong template sẽ chạy lại ở mỗi lần Angular kiểm tra thay đổi.
  protected readonly rows = computed(() =>
    this.quizzes().map((quiz) => ({
      ...quiz,
      day: relativeDay(new Date(quiz.submittedAt)),
    })),
  );
}
