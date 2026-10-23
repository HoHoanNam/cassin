import { ChangeDetectionStrategy, Component, computed, inject, input, signal } from '@angular/core';
import { rxResource } from '@angular/core/rxjs-interop';
import { RouterLink } from '@angular/router';

import { QuizQuestionItem } from './quiz-question/quiz-question';
import { QuizResult } from './quiz.models';
import { QuizService } from './quiz.service';

/**
 * Trang làm bài kiểm tra, trình bày như một tờ giấy kiểm tra:
 * ô "Điểm" và "Lời phê của giáo viên" được điền bằng bút đỏ sau khi nộp.
 */
@Component({
  selector: 'app-quiz-page',
  imports: [RouterLink, QuizQuestionItem],
  templateUrl: './quiz-page.html',
  styleUrl: './quiz-page.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class QuizPage {
  private readonly quizService = inject(QuizService);

  /** :id của chủ đề ngữ pháp trên URL /grammar/:id/quiz */
  readonly id = input.required<string>();

  protected readonly topicId = computed(() => Number(this.id()));

  protected readonly session = rxResource({
    params: () => ({ topicId: this.topicId() }),
    stream: ({ params }) => this.quizService.start(params.topicId),
  });

  /** Đáp án đang chọn: key = id câu hỏi, value = id đáp án. */
  protected readonly selections = signal<Record<number, number>>({});
  protected readonly result = signal<QuizResult | null>(null);
  protected readonly submitting = signal(false);

  protected readonly questions = computed(() => this.session.value()?.questions ?? []);
  protected readonly answeredCount = computed(() => Object.keys(this.selections()).length);
  protected readonly unansweredCount = computed(
    () => this.questions().length - this.answeredCount(),
  );

  /** Tra kết quả theo id câu hỏi (Map: tra nhanh, không phải duyệt cả danh sách mỗi lần). */
  protected readonly resultByQuestion = computed(
    () => new Map(this.result()?.answers.map((a) => [a.questionId, a]) ?? []),
  );

  /** Lời phê theo tỉ lệ đúng, kiểu cô giáo vẫn viết. */
  protected readonly teacherComment = computed(() => {
    const r = this.result();
    if (!r) {
      return '';
    }
    const ratio = r.score / r.total;
    if (ratio === 1) return 'Rất tốt! Em nắm chắc bài.';
    if (ratio >= 0.7) return 'Làm tốt, cố gắng thêm nhé.';
    if (ratio >= 0.5) return 'Em cần xem lại phần lý thuyết.';
    return 'Em đọc lại bài giải thích rồi làm lại nhé.';
  });

  protected choose(questionId: number, optionId: number): void {
    if (this.result()) {
      return;
    }
    // Tạo object mới thay vì sửa object cũ: signal chỉ báo thay đổi khi giá trị là object khác.
    this.selections.update((current) => ({ ...current, [questionId]: optionId }));
  }

  protected submit(): void {
    if (this.submitting() || this.result()) {
      return;
    }
    this.submitting.set(true);
    this.quizService.submit(this.topicId(), this.selections()).subscribe((result) => {
      this.result.set(result);
      this.submitting.set(false);
      // Cuộn lên đầu tờ giấy để thấy điểm ngay.
      window.scrollTo({ top: 0, behavior: 'smooth' });
    });
  }

  /** Làm lại: lượt mới, đề mới (server sẽ chọn ngẫu nhiên lại). */
  protected retry(): void {
    this.selections.set({});
    this.result.set(null);
    this.session.reload();
  }
}
