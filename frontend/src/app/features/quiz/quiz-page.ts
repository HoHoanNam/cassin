import { ChangeDetectionStrategy, Component, computed, inject, input, numberAttribute, signal } from '@angular/core';
import { rxResource } from '@angular/core/rxjs-interop';
import { Router, RouterLink } from '@angular/router';
import { Observable, Subject } from 'rxjs';

import { QuizApi } from '../../core/api/quiz.api';
import { ApiError, errorMessage } from '../../core/models/common.models';
import { ToastService } from '../../core/ui/toast.service';
import { ConfirmDialog } from '../../shared/ui/confirm-dialog/confirm-dialog';
import { EmptyState } from '../../shared/ui/empty-state/empty-state';
import { ErrorState } from '../../shared/ui/error-state/error-state';
import { Icon } from '../../shared/ui/icon/icon';
import { ConfirmLeave } from './quiz-leave.guard';
import { QuizQuestionView } from './quiz-question/quiz-question';

/**
 * Làm bài kiểm tra (chế độ tập trung). Mở trang = gọi API "bắt đầu" → server chốt đề.
 * Mỗi màn hình một câu; thanh số câu phía trên cho nhảy tới câu bất kỳ.
 * Phím tắt: A–D hoặc 1–4 chọn đáp án, ← → chuyển câu.
 */
@Component({
  selector: 'app-quiz-page',
  imports: [RouterLink, ConfirmDialog, EmptyState, ErrorState, Icon, QuizQuestionView],
  templateUrl: './quiz-page.html',
  styleUrl: './quiz-page.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: { '(document:keydown)': 'onKey($event)' },
})
export class QuizPage implements ConfirmLeave {
  readonly topicId = input.required({ transform: numberAttribute });

  private readonly api = inject(QuizApi);
  private readonly router = inject(Router);
  private readonly toast = inject(ToastService);

  protected readonly quiz = rxResource({
    params: () => this.topicId(),
    stream: ({ params }) => this.api.start(params),
  });

  /** Đáp án đã chọn: questionId → optionId */
  protected readonly answers = signal<Record<number, number>>({});
  protected readonly index = signal(0);
  protected readonly submitting = signal(false);
  protected readonly confirmSubmit = signal(false);
  private submitted = false;

  // Hỏi lại khi rời trang: guard chờ người dùng bấm một trong hai nút.
  protected readonly leaveDialog = signal(false);
  private leaveAnswer: Subject<boolean> | null = null;

  protected readonly questions = computed(() => (this.quiz.hasValue() ? this.quiz.value().questions : []));
  protected readonly current = computed(() => this.questions()[this.index()] ?? null);
  protected readonly answeredCount = computed(() => Object.keys(this.answers()).length);
  protected readonly unanswered = computed(() => this.questions().length - this.answeredCount());
  protected readonly isLast = computed(() => this.index() === this.questions().length - 1);
  /** Câu báo lỗi lấy từ server (VD: "Không tìm thấy chủ đề"), thay vì câu chung chung về mạng. */
  protected readonly errorText = computed(() => errorMessage(this.quiz.error(), 'Kiểm tra kết nối mạng rồi thử lại.'));
  protected readonly noQuestions = computed(() => {
    const error = this.quiz.error();
    return error instanceof ApiError && error.code === 'TOPIC_HAS_NO_QUESTIONS';
  });

  protected choose(questionId: number, optionId: number): void {
    this.answers.update((a) => ({ ...a, [questionId]: optionId }));
  }

  protected goTo(i: number): void {
    if (i >= 0 && i < this.questions().length) {
      this.index.set(i);
    }
  }

  /** Còn câu bỏ trống → hỏi lại trước; đã làm hết → nộp luôn. */
  protected requestSubmit(): void {
    if (this.unanswered() > 0) {
      this.confirmSubmit.set(true);
    } else {
      this.submit();
    }
  }

  protected submit(): void {
    const quiz = this.quiz.value();
    if (!quiz || this.submitting()) {
      return;
    }
    this.submitting.set(true);
    const answers = quiz.questions.map((q) => ({ questionId: q.questionId, optionId: this.answers()[q.questionId] ?? null }));
    this.api.submit(quiz.attemptId, { answers }).subscribe({
      next: () => {
        this.submitted = true;
        void this.router.navigate(['/quiz', quiz.attemptId, 'result'], { replaceUrl: true });
      },
      error: (error: unknown) => {
        this.submitting.set(false);
        this.confirmSubmit.set(false);
        if (error instanceof ApiError && error.code === 'QUIZ_ALREADY_SUBMITTED') {
          this.submitted = true;
          void this.router.navigate(['/quiz', quiz.attemptId, 'result'], { replaceUrl: true });
          return;
        }
        this.toast.error(`Chưa nộp được bài. ${errorMessage(error)}`, { label: 'Thử lại', run: () => this.submit() });
      },
    });
  }

  confirmLeave(): boolean | Observable<boolean> {
    if (this.submitted || this.answeredCount() === 0) {
      return true;
    }
    this.leaveAnswer = new Subject<boolean>();
    this.leaveDialog.set(true);
    return this.leaveAnswer.asObservable();
  }

  protected answerLeave(leave: boolean): void {
    this.leaveDialog.set(false);
    this.leaveAnswer?.next(leave);
    this.leaveAnswer?.complete();
    this.leaveAnswer = null;
  }

  protected onKey(event: KeyboardEvent): void {
    const question = this.current();
    if (!question || this.confirmSubmit() || this.leaveDialog() || event.ctrlKey || event.metaKey || event.altKey) {
      return;
    }
    const key = event.key.toLowerCase();
    const optionIndex = ['a', 'b', 'c', 'd'].indexOf(key) >= 0 ? ['a', 'b', 'c', 'd'].indexOf(key) : ['1', '2', '3', '4'].indexOf(key);
    if (optionIndex >= 0 && question.options[optionIndex]) {
      this.choose(question.questionId, question.options[optionIndex].id);
    } else if (event.key === 'ArrowRight' && !(event.target instanceof HTMLInputElement)) {
      this.goTo(this.index() + 1);
    } else if (event.key === 'ArrowLeft' && !(event.target instanceof HTMLInputElement)) {
      this.goTo(this.index() - 1);
    }
  }
}
