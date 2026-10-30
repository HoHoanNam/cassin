import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { rxResource } from '@angular/core/rxjs-interop';
import { Router, RouterLink } from '@angular/router';

import { AdminApi } from '../../core/api/admin.api';
import { GrammarApi } from '../../core/api/grammar.api';
import { errorMessage } from '../../core/models/common.models';
import { CEFR_LEVELS, CefrLevel, TopicDetail, TopicSummary } from '../../core/models/grammar.models';
import { ToastService } from '../../core/ui/toast.service';
import { ConfirmDialog } from '../../shared/ui/confirm-dialog/confirm-dialog';
import { EmptyState } from '../../shared/ui/empty-state/empty-state';
import { ErrorState } from '../../shared/ui/error-state/error-state';
import { Icon } from '../../shared/ui/icon/icon';
import { LevelBadge } from '../../shared/ui/level-badge/level-badge';
import { Skeleton } from '../../shared/ui/skeleton/skeleton';
import { TopicFormDialog } from './topic-form-dialog/topic-form-dialog';

/**
 * Danh sách chủ đề ngữ pháp để quản trị.
 * Đọc: GET /api/grammar/topics (giống trang học viên). Ghi: /api/admin/topics.
 */
@Component({
  selector: 'app-admin-topics-page',
  imports: [RouterLink, ConfirmDialog, EmptyState, ErrorState, Icon, LevelBadge, Skeleton, TopicFormDialog],
  templateUrl: './admin-topics-page.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class AdminTopicsPage {
  private readonly grammarApi = inject(GrammarApi);
  private readonly adminApi = inject(AdminApi);
  private readonly router = inject(Router);
  private readonly toast = inject(ToastService);

  protected readonly levels = CEFR_LEVELS;
  protected readonly level = signal<CefrLevel | null>(null);

  protected readonly topics = rxResource({
    params: () => this.level(),
    stream: ({ params }) => this.grammarApi.listTopics(params),
  });

  protected readonly creating = signal(false);
  protected readonly toDelete = signal<TopicSummary | null>(null);
  protected readonly deleting = signal(false);

  protected onCreated(topic: TopicDetail): void {
    this.creating.set(false);
    this.toast.success(`Đã tạo chủ đề "${topic.title}". Soạn câu hỏi cho chủ đề ngay bây giờ.`);
    void this.router.navigate(['/admin/grammar', topic.id]);
  }

  protected deleteTopic(): void {
    const topic = this.toDelete();
    if (!topic) {
      return;
    }
    this.deleting.set(true);
    this.adminApi.deleteTopic(topic.id).subscribe({
      next: () => {
        this.deleting.set(false);
        this.toDelete.set(null);
        this.toast.success(`Đã xóa chủ đề "${topic.title}"`);
        this.topics.reload();
      },
      error: (error: unknown) => {
        // 409 TOPIC_IN_USE: "Chủ đề đã có người làm bài, không thể xóa"
        this.deleting.set(false);
        this.toDelete.set(null);
        this.toast.error(errorMessage(error));
      },
    });
  }
}
