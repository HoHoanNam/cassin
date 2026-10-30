import { ChangeDetectionStrategy, Component, computed, inject, input, numberAttribute, signal } from '@angular/core';
import { rxResource } from '@angular/core/rxjs-interop';
import { Router, RouterLink } from '@angular/router';

import { AdminApi } from '../../core/api/admin.api';
import { GrammarApi } from '../../core/api/grammar.api';
import { AdminQuestion } from '../../core/models/admin.models';
import { ApiError, errorMessage } from '../../core/models/common.models';
import { TopicDetail } from '../../core/models/grammar.models';
import { ToastService } from '../../core/ui/toast.service';
import { OPTION_LETTERS } from '../../core/util/text';
import { ConfirmDialog } from '../../shared/ui/confirm-dialog/confirm-dialog';
import { EmptyState } from '../../shared/ui/empty-state/empty-state';
import { ErrorState } from '../../shared/ui/error-state/error-state';
import { Icon } from '../../shared/ui/icon/icon';
import { LevelBadge } from '../../shared/ui/level-badge/level-badge';
import { Skeleton } from '../../shared/ui/skeleton/skeleton';
import { QuestionFormDialog } from './question-form-dialog/question-form-dialog';
import { TopicFormDialog } from './topic-form-dialog/topic-form-dialog';

/**
 * Quản lý MỘT chủ đề: sửa / xóa chủ đề, soạn câu hỏi.
 * Câu hỏi lấy từ GET /api/admin/topics/{id}/questions: CÓ đáp án đúng (API học viên giấu field này).
 */
@Component({
  selector: 'app-admin-topic-page',
  imports: [RouterLink, ConfirmDialog, EmptyState, ErrorState, Icon, LevelBadge, QuestionFormDialog, Skeleton, TopicFormDialog],
  templateUrl: './admin-topic-page.html',
  styleUrl: './admin-topic-page.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class AdminTopicPage {
  readonly topicId = input.required({ transform: numberAttribute });

  private readonly grammarApi = inject(GrammarApi);
  private readonly adminApi = inject(AdminApi);
  private readonly router = inject(Router);
  private readonly toast = inject(ToastService);

  protected readonly letters = OPTION_LETTERS;

  protected readonly topic = rxResource({
    params: () => this.topicId(),
    stream: ({ params }) => this.grammarApi.getTopic(params),
  });

  protected readonly questions = rxResource({
    params: () => this.topicId(),
    stream: ({ params }) => this.adminApi.listQuestions(params),
  });

  protected readonly notFound = computed(() => {
    const error = this.topic.error();
    return error instanceof ApiError && error.status === 404;
  });

  protected readonly editingTopic = signal(false);
  protected readonly confirmDeleteTopic = signal(false);
  protected readonly questionDialogOpen = signal(false);
  protected readonly editingQuestion = signal<AdminQuestion | null>(null);
  protected readonly questionToDelete = signal<AdminQuestion | null>(null);
  protected readonly deleting = signal(false);

  protected openAddQuestion(): void {
    this.editingQuestion.set(null);
    this.questionDialogOpen.set(true);
  }

  protected openEditQuestion(question: AdminQuestion): void {
    this.editingQuestion.set(question);
    this.questionDialogOpen.set(true);
  }

  protected onTopicSaved(topic: TopicDetail): void {
    this.editingTopic.set(false);
    this.toast.success(`Đã lưu chủ đề "${topic.title}"`);
    this.topic.reload();
  }

  protected onQuestionSaved(event: { question: AdminQuestion; keepOpen: boolean }): void {
    this.toast.success(this.editingQuestion() ? 'Đã lưu câu hỏi' : 'Đã thêm câu hỏi');
    if (!event.keepOpen) {
      this.questionDialogOpen.set(false);
    }
    this.questions.reload();
    this.topic.reload();
  }

  protected deleteQuestion(): void {
    const question = this.questionToDelete();
    if (!question) {
      return;
    }
    this.deleting.set(true);
    this.adminApi.deleteQuestion(question.questionId).subscribe({
      next: () => {
        this.deleting.set(false);
        this.questionToDelete.set(null);
        this.toast.success('Đã xóa câu hỏi');
        this.questions.reload();
        this.topic.reload();
      },
      error: (error: unknown) => {
        // 409 QUESTION_IN_USE: "Câu hỏi đã có người trả lời, hãy sửa thay vì xóa"
        this.deleting.set(false);
        this.questionToDelete.set(null);
        this.toast.error(errorMessage(error));
      },
    });
  }

  protected deleteTopic(): void {
    const topic = this.topic.value();
    this.deleting.set(true);
    this.adminApi.deleteTopic(this.topicId()).subscribe({
      next: () => {
        this.deleting.set(false);
        this.toast.success(`Đã xóa chủ đề "${topic?.title}"`);
        void this.router.navigateByUrl('/admin/grammar');
      },
      error: (error: unknown) => {
        this.deleting.set(false);
        this.confirmDeleteTopic.set(false);
        this.toast.error(errorMessage(error));
      },
    });
  }
}
