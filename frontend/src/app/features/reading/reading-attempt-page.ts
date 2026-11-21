import { ChangeDetectionStrategy, Component, computed, effect, inject, input, numberAttribute, signal } from '@angular/core';
import { rxResource } from '@angular/core/rxjs-interop';
import { Router, RouterLink } from '@angular/router';

import { PassageApi } from '../../core/api/passage.api';
import { ApiError, errorMessage } from '../../core/models/common.models';
import { AnswerRequest, HintView, QuestionView } from '../../core/models/passage.models';
import { ToastService } from '../../core/ui/toast.service';
import { Mark } from '../../core/util/passage-text';
import { ConfirmDialog } from '../../shared/ui/confirm-dialog/confirm-dialog';
import { EmptyState } from '../../shared/ui/empty-state/empty-state';
import { ErrorState } from '../../shared/ui/error-state/error-state';
import { Icon } from '../../shared/ui/icon/icon';
import { LevelBadge } from '../../shared/ui/level-badge/level-badge';
import { PassageText } from './passage-text/passage-text';
import { DraftAnswer, ReadingQuestion } from './reading-question/reading-question';

/**
 * [THÊM] Làm bài đọc (chế độ tập trung, không có sidebar).
 *
 *   Mở trang ──POST /passages/{id}/attempts──▶ 201 lượt mới · 200 làm tiếp lượt dở (gợi ý đã mở vẫn còn)
 *   Bấm "Gợi ý" ──POST .../hint──▶ cặp đồng nghĩa, cụm trong bài được tô vàng
 *   Nộp ──POST .../submit──▶ sang trang kết quả
 *
 * Máy tính: bài đọc bên trái, câu hỏi bên phải (như đề IELTS trên máy).
 * Điện thoại: hai tab "Bài đọc" / "Câu hỏi".
 * Câu trả lời lưu nháp trong trình duyệt theo từng lượt: tải lại trang không mất bài đang làm.
 */
@Component({
  selector: 'app-reading-attempt-page',
  imports: [RouterLink, ConfirmDialog, EmptyState, ErrorState, Icon, LevelBadge, PassageText, ReadingQuestion],
  templateUrl: './reading-attempt-page.html',
  styleUrl: './reading-attempt-page.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ReadingAttemptPage {
  readonly passageId = input.required({ transform: numberAttribute });

  private readonly api = inject(PassageApi);
  private readonly router = inject(Router);
  private readonly toast = inject(ToastService);

  protected readonly started = rxResource({
    params: () => this.passageId(),
    stream: ({ params }) => this.api.start(params),
  });

  protected readonly answers = signal<Record<number, DraftAnswer>>({});
  /** Gợi ý đã mở theo câu. Khởi tạo từ server (làm tiếp lượt dở), cập nhật mỗi lần bấm "Gợi ý". */
  protected readonly hints = signal<Record<number, { shown: HintView[]; remaining: number }>>({});
  protected readonly hintBusy = signal<number | null>(null);
  protected readonly tab = signal<'passage' | 'questions'>('passage');
  protected readonly submitting = signal(false);
  protected readonly confirmSubmit = signal(false);

  protected readonly attempt = computed(() => (this.started.hasValue() ? this.started.value().attempt : null));
  protected readonly questions = computed(() => this.attempt()?.questions ?? []);
  protected readonly answeredCount = computed(() => this.questions().filter((q) => isAnswered(this.answers()[q.id])).length);
  protected readonly unanswered = computed(() => this.questions().length - this.answeredCount());

  /** Mọi cụm đồng nghĩa đã mở → tô vàng trong bài. */
  protected readonly marks = computed<Mark[]>(() =>
    Object.values(this.hints()).flatMap((h) => h.shown.map((hint) => ({ text: hint.passagePhrase, kind: 'hint' as const }))),
  );
  protected readonly lastHintKey = signal<number | null>(null);

  protected readonly notFound = computed(() => {
    const error = this.started.error();
    return error instanceof ApiError && error.status === 404;
  });
  protected readonly errorText = computed(() => errorMessage(this.started.error(), 'Kiểm tra kết nối mạng rồi thử lại.'));

  constructor() {
    // Có đề → nạp gợi ý đã mở + bản nháp câu trả lời của lượt này.
    effect(() => {
      const value = this.started.hasValue() ? this.started.value() : null;
      if (!value) {
        return;
      }
      const { attempt, created } = value;
      this.hints.set(
        Object.fromEntries(attempt.questions.map((q) => [q.id, { shown: q.revealedHints, remaining: q.hintsRemaining }])),
      );
      this.answers.set(created ? {} : loadDraft(attempt.attemptId));
      if (!created) {
        this.toast.info('Bạn đang làm tiếp lượt chưa nộp của bài này.');
      }
    });
    // Lưu nháp mỗi lần trả lời.
    effect(() => {
      const attempt = this.attempt();
      if (attempt) {
        saveDraft(attempt.attemptId, this.answers());
      }
    });
  }

  protected answerOf(question: QuestionView): DraftAnswer | undefined {
    return this.answers()[question.id];
  }

  protected setAnswer(questionId: number, answer: DraftAnswer): void {
    this.answers.update((all) => ({ ...all, [questionId]: answer }));
  }

  protected askHint(question: QuestionView): void {
    const attempt = this.attempt();
    if (!attempt || this.hintBusy() !== null) {
      return;
    }
    this.hintBusy.set(question.id);
    this.api.revealHint(attempt.attemptId, question.id).subscribe({
      next: (response) => {
        this.hintBusy.set(null);
        this.hints.update((all) => ({
          ...all,
          [question.id]: { shown: [...(all[question.id]?.shown ?? []), response.hint], remaining: response.hintsRemaining },
        }));
        this.lastHintKey.update((k) => (k ?? 0) + 1); // cuộn bài đọc tới chỗ vừa tô
      },
      error: (error: unknown) => {
        this.hintBusy.set(null);
        if (error instanceof ApiError && error.code === 'NO_MORE_HINTS') {
          this.hints.update((all) => ({ ...all, [question.id]: { ...all[question.id], remaining: 0 } }));
        }
        this.toast.error(errorMessage(error));
      },
    });
  }

  /** Còn câu bỏ trống → hỏi lại trước; làm hết → nộp luôn. */
  protected requestSubmit(): void {
    if (this.unanswered() > 0) {
      this.confirmSubmit.set(true);
    } else {
      this.submit();
    }
  }

  protected submit(): void {
    const attempt = this.attempt();
    if (!attempt || this.submitting()) {
      return;
    }
    this.submitting.set(true);
    const answers: AnswerRequest[] = attempt.questions
      .filter((q) => isAnswered(this.answers()[q.id]))
      .map((q) => ({ questionId: q.id, ...this.answers()[q.id] }));
    this.api.submit(attempt.attemptId, { answers }).subscribe({
      next: () => this.goToResult(attempt.attemptId),
      error: (error: unknown) => {
        this.submitting.set(false);
        this.confirmSubmit.set(false);
        if (error instanceof ApiError && error.code === 'ATTEMPT_SUBMITTED') {
          this.goToResult(attempt.attemptId);
          return;
        }
        this.toast.error(`Chưa nộp được bài. ${errorMessage(error)}`, { label: 'Thử lại', run: () => this.submit() });
      },
    });
  }

  private goToResult(attemptId: number): void {
    clearDraft(attemptId);
    void this.router.navigate(['/reading/attempts', attemptId, 'result'], { replaceUrl: true });
  }
}

function isAnswered(answer: DraftAnswer | undefined): boolean {
  return !!answer && (answer.optionId != null || answer.tfngAnswer != null || !!answer.text?.trim());
}

// ───────── Nháp câu trả lời: chỉ là tiện ích trên một trình duyệt, mất cũng không sao ─────────

const draftKey = (attemptId: number) => `cassin.reading.draft.${attemptId}`;

function loadDraft(attemptId: number): Record<number, DraftAnswer> {
  try {
    return JSON.parse(localStorage.getItem(draftKey(attemptId)) ?? '{}') as Record<number, DraftAnswer>;
  } catch {
    return {};
  }
}

function saveDraft(attemptId: number, answers: Record<number, DraftAnswer>): void {
  try {
    localStorage.setItem(draftKey(attemptId), JSON.stringify(answers));
  } catch {
    // Trình duyệt chặn lưu (chế độ ẩn danh...): bỏ qua, chỉ mất bản nháp.
  }
}

function clearDraft(attemptId: number): void {
  try {
    localStorage.removeItem(draftKey(attemptId));
  } catch {
    // như trên
  }
}
