import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { rxResource } from '@angular/core/rxjs-interop';
import { RouterLink } from '@angular/router';

import { PassageApi } from '../../core/api/passage.api';
import { CEFR_LEVELS, CefrLevel } from '../../core/models/grammar.models';
import { EmptyState } from '../../shared/ui/empty-state/empty-state';
import { ErrorState } from '../../shared/ui/error-state/error-state';
import { Icon } from '../../shared/ui/icon/icon';
import { LevelBadge } from '../../shared/ui/level-badge/level-badge';
import { Pager } from '../../shared/ui/pager/pager';
import { ScoreStamp } from '../../shared/ui/score-stamp/score-stamp';
import { Skeleton } from '../../shared/ui/skeleton/skeleton';

/** [THÊM] Danh sách bài đọc hiểu đang mở, lọc theo trình độ. Bài đã làm hiện điểm cao nhất. */
@Component({
  selector: 'app-reading-page',
  imports: [RouterLink, EmptyState, ErrorState, Icon, LevelBadge, Pager, ScoreStamp, Skeleton],
  templateUrl: './reading-page.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ReadingPage {
  private readonly api = inject(PassageApi);

  protected readonly levels = CEFR_LEVELS;
  protected readonly level = signal<CefrLevel | null>(null);
  protected readonly page = signal(0);

  /** level hoặc page đổi → gọi lại API. */
  protected readonly passages = rxResource({
    params: () => ({ level: this.level(), page: this.page() }),
    stream: ({ params }) => this.api.list(params.level, params.page, 20),
  });

  protected pickLevel(level: CefrLevel | null): void {
    this.level.set(level);
    this.page.set(0);
  }
}
