import { ChangeDetectionStrategy, Component, computed, input, output } from '@angular/core';

import { QuizAnswerResult, QuizQuestion } from '../quiz.models';

type OptionState = 'idle' | 'selected' | 'correct' | 'wrong';

/**
 * Một câu trắc nghiệm. Không tự giữ trạng thái: cha truyền xuống đáp án đang chọn
 * và kết quả (nếu đã nộp), câu hỏi chỉ báo lên (picked) khi người dùng chọn.
 */
@Component({
  selector: 'app-quiz-question',
  templateUrl: './quiz-question.html',
  styleUrl: './quiz-question.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class QuizQuestionItem {
  readonly number = input.required<number>();
  readonly question = input.required<QuizQuestion>();
  readonly selectedId = input<number | null>(null);
  /** null = chưa nộp bài. */
  readonly result = input<QuizAnswerResult | null>(null);

  // Không đặt tên "select": trùng tên sự kiện có sẵn của trình duyệt.
  readonly picked = output<number>();

  protected readonly letters = ['A', 'B', 'C', 'D'];

  /** Trạng thái hiển thị của từng đáp án, tính một lần mỗi khi dữ liệu đổi. */
  protected readonly states = computed(() => {
    const result = this.result();
    const selected = this.selectedId();
    const map = new Map<number, OptionState>();
    for (const option of this.question().options) {
      let state: OptionState = 'idle';
      if (result) {
        if (option.id === result.correctOptionId) {
          state = 'correct';
        } else if (option.id === result.selectedOptionId) {
          state = 'wrong';
        }
      } else if (option.id === selected) {
        state = 'selected';
      }
      map.set(option.id, state);
    }
    return map;
  });
}
