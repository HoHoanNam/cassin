import { ChangeDetectionStrategy, Component, computed, effect, inject, input, signal } from '@angular/core';
import { rxResource, toSignal } from '@angular/core/rxjs-interop';
import { NonNullableFormBuilder, ReactiveFormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { Observable, map, of } from 'rxjs';

import { PassageApi } from '../../core/api/passage.api';
import { ApiError, errorMessage } from '../../core/models/common.models';
import { CEFR_LEVELS } from '../../core/models/grammar.models';
import { AdminPassage, PASSAGE_LIMITS, PASSAGE_STATUS_LABEL, QUESTION_TYPE_LABEL, QuestionType } from '../../core/models/passage.models';
import { ToastService } from '../../core/ui/toast.service';
import { countWords, sentences } from '../../core/util/passage-text';
import { ConfirmDialog } from '../../shared/ui/confirm-dialog/confirm-dialog';
import { EmptyState } from '../../shared/ui/empty-state/empty-state';
import { ErrorState } from '../../shared/ui/error-state/error-state';
import { Icon } from '../../shared/ui/icon/icon';
import { Skeleton } from '../../shared/ui/skeleton/skeleton';
import { createPassageForm, createQuestionForm, fillPassageForm, toPassageRequest } from './passage-editor/passage-form';
import { PassageQuestionEditor } from './passage-editor/passage-question-editor';

/**
 * [THÊM] Soạn MỘT bài đọc: nội dung + toàn bộ câu hỏi trên cùng một trang, lưu một lần (PUT thay toàn bộ).
 *
 *   /admin/passages/new         → POST, xong chuyển sang /admin/passages/{id}
 *   /admin/passages/{id}        → PUT; Publish / Lưu trữ / Xóa theo trạng thái
 *   Bài đã có người làm (locked) hoặc đã lưu trữ → form chỉ đọc
 */
@Component({
  selector: 'app-admin-passage-page',
  imports: [ReactiveFormsModule, RouterLink, ConfirmDialog, EmptyState, ErrorState, Icon, PassageQuestionEditor, Skeleton],
  templateUrl: './admin-passage-page.html',
  styleUrl: './admin-passage-page.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class AdminPassagePage {
  /** Không có (route /new) → soạn bài mới */
  readonly passageId = input<number | null, string | undefined>(null, { transform: (v) => (v ? Number(v) : null) });

  private readonly api = inject(PassageApi);
  private readonly fb = inject(NonNullableFormBuilder);
  private readonly router = inject(Router);
  private readonly toast = inject(ToastService);

  protected readonly levels = CEFR_LEVELS;
  protected readonly limits = PASSAGE_LIMITS;
  protected readonly statusLabel = PASSAGE_STATUS_LABEL;
  protected readonly typeLabels = Object.entries(QUESTION_TYPE_LABEL) as [QuestionType, string][];

  protected readonly form = createPassageForm(this.fb);

  /** Bài đang sửa (null khi soạn mới). Cập nhật sau mỗi lần lưu / publish / lưu trữ. */
  protected readonly passage = signal<AdminPassage | null>(null);
  protected readonly loader = rxResource({
    params: () => this.passageId(),
    stream: ({ params }): Observable<AdminPassage | null> => (params ? this.api.adminGet(params) : of(null)),
  });

  protected readonly saving = signal(false);
  protected readonly serverError = signal<string | null>(null);
  protected readonly confirmDelete = signal(false);
  protected readonly confirmArchive = signal(false);

  private readonly content = toSignal(this.form.controls.content.valueChanges.pipe(map((v) => v ?? '')), { initialValue: '' });
  protected readonly sentences = computed(() => sentences(this.content()));
  protected readonly wordCount = computed(() => countWords(this.content()));

  /** Đã có người làm hoặc đã lưu trữ → chỉ xem */
  protected readonly readonly = computed(() => {
    const p = this.passage();
    return !!p && (p.locked || p.status === 'ARCHIVED');
  });

  protected readonly notFound = computed(() => {
    const error = this.loader.error();
    return error instanceof ApiError && error.status === 404;
  });

  constructor() {
    effect(() => {
      if (this.loader.hasValue()) {
        this.load(this.loader.value());
      }
    });
  }

  protected get questions() {
    return this.form.controls.questions;
  }

  protected addQuestion(type: QuestionType): void {
    this.questions.push(createQuestionForm(this.fb, type));
    this.form.markAsDirty();
  }

  protected removeQuestion(i: number): void {
    this.questions.removeAt(i);
    this.form.markAsDirty();
  }

  protected moveQuestion(i: number, delta: -1 | 1): void {
    const control = this.questions.at(i);
    this.questions.removeAt(i, { emitEvent: false });
    this.questions.insert(i + delta, control);
    this.form.markAsDirty();
  }

  protected save(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      this.serverError.set('Còn chỗ chưa hợp lệ, xem các ô báo đỏ bên dưới.');
      return;
    }
    const request = toPassageRequest(this.form);
    const current = this.passage();
    this.run(current ? this.api.adminUpdate(current.id, request) : this.api.adminCreate(request), (saved) => {
      this.toast.success('Đã lưu bài đọc');
      if (!current) {
        void this.router.navigate(['/admin/passages', saved.id], { replaceUrl: true });
      }
    });
  }

  protected publish(): void {
    const current = this.passage();
    if (current) {
      this.run(this.api.adminPublish(current.id), () => this.toast.success('Bài đọc đã hiện với học viên'));
    }
  }

  protected archive(): void {
    const current = this.passage();
    if (current) {
      this.run(this.api.adminArchive(current.id), () => {
        this.confirmArchive.set(false);
        this.toast.success('Đã lưu trữ bài đọc. Học viên không thấy bài này nữa, lịch sử làm bài vẫn giữ.');
      });
    }
  }

  protected remove(): void {
    const current = this.passage();
    if (!current) {
      return;
    }
    this.saving.set(true);
    this.api.adminDelete(current.id).subscribe({
      next: () => {
        this.toast.success(`Đã xóa bài "${current.title}"`);
        void this.router.navigate(['/admin/passages']);
      },
      error: (error: unknown) => {
        this.saving.set(false);
        this.confirmDelete.set(false);
        this.toast.error(errorMessage(error)); // 409 PASSAGE_LOCKED
      },
    });
  }

  /** Gọi API ghi, cập nhật lại form theo bản server trả về (id câu hỏi mới, trạng thái mới...). */
  private run(call: Observable<AdminPassage>, done: (saved: AdminPassage) => void): void {
    this.saving.set(true);
    this.serverError.set(null);
    call.subscribe({
      next: (saved) => {
        this.saving.set(false);
        this.load(saved);
        done(saved);
      },
      error: (error: unknown) => {
        this.saving.set(false);
        // 400 INVALID_PASSAGE: "Câu 3: câu bằng chứng phải trích nguyên văn..." → hiện nguyên câu.
        this.serverError.set(errorMessage(error));
        this.confirmArchive.set(false);
        if (error instanceof ApiError && error.code === 'PASSAGE_LOCKED') {
          this.loader.reload(); // có người vừa làm bài → tải lại để khóa form
        }
      },
    });
  }

  private load(passage: AdminPassage | null): void {
    this.passage.set(passage);
    if (passage) {
      fillPassageForm(this.fb, this.form, passage);
    }
    if (this.readonly()) {
      this.form.disable({ emitEvent: false });
    } else {
      this.form.enable({ emitEvent: false });
    }
    this.form.markAsPristine();
  }
}
