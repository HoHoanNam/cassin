import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { rxResource } from '@angular/core/rxjs-interop';
import { RouterLink } from '@angular/router';

import { PassageApi } from '../../core/api/passage.api';
import { PASSAGE_STATUS_LABEL, PassageStatus } from '../../core/models/passage.models';
import { relativeDay } from '../../core/util/dates';
import { EmptyState } from '../../shared/ui/empty-state/empty-state';
import { ErrorState } from '../../shared/ui/error-state/error-state';
import { Icon } from '../../shared/ui/icon/icon';
import { LevelBadge } from '../../shared/ui/level-badge/level-badge';
import { Pager } from '../../shared/ui/pager/pager';
import { Skeleton } from '../../shared/ui/skeleton/skeleton';

/** [THÊM] Danh sách bài đọc để quản trị, lọc theo trạng thái. Sửa mới nhất lên đầu. */
@Component({
  selector: 'app-admin-passages-page',
  imports: [RouterLink, EmptyState, ErrorState, Icon, LevelBadge, Pager, Skeleton],
  templateUrl: './admin-passages-page.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class AdminPassagesPage {
  private readonly api = inject(PassageApi);

  protected readonly statusLabel = PASSAGE_STATUS_LABEL;
  protected readonly statuses = Object.keys(PASSAGE_STATUS_LABEL) as PassageStatus[];
  protected readonly relativeDay = relativeDay;
  protected readonly status = signal<PassageStatus | null>(null);
  protected readonly page = signal(0);

  protected readonly passages = rxResource({
    params: () => ({ status: this.status(), page: this.page() }),
    stream: ({ params }) => this.api.adminList(params.status, params.page, 20),
  });

  protected pickStatus(status: PassageStatus | null): void {
    this.status.set(status);
    this.page.set(0);
  }
}
