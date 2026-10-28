import { ChangeDetectionStrategy, Component, input, output } from '@angular/core';

import { QuizQuestion } from '../../../core/models/quiz.models';
import { OPTION_LETTERS } from '../../../core/util/text';

/**
 * Một câu hỏi trắc nghiệm lúc làm bài: 4 đáp án dạng radio.
 * Dùng <input type="radio"> thật (ẩn đi, vẽ lại bằng CSS) nên phím mũi tên,
 * Tab và trình đọc màn hình đều hoạt động như một nhóm lựa chọn chuẩn.
 */
@Component({
  selector: 'app-quiz-question',
  templateUrl: './quiz-question.html',
  styleUrl: './quiz-question.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class QuizQuestionView {
  readonly question = input.required<QuizQuestion>();
  readonly total = input.required<number>();
  readonly selected = input<number | null>(null);
  readonly select = output<number>();

  protected readonly letters = OPTION_LETTERS;
}
