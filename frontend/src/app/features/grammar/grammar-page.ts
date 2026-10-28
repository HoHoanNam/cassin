import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { rxResource } from '@angular/core/rxjs-interop';
import { RouterLink } from '@angular/router';

import { GrammarApi } from '../../core/api/grammar.api';
import { CEFR_LEVELS, CefrLevel } from '../../core/models/grammar.models';
import { EmptyState } from '../../shared/ui/empty-state/empty-state';
import { ErrorState } from '../../shared/ui/error-state/error-state';
import { LevelBadge } from '../../shared/ui/level-badge/level-badge';
import { Skeleton } from '../../shared/ui/skeleton/skeleton';

/** Danh sách chủ đề ngữ pháp, lọc theo cấp độ CEFR (lọc ở backend qua ?level=). */
@Component({
  selector: 'app-grammar-page',
  imports: [RouterLink, EmptyState, ErrorState, LevelBadge, Skeleton],
  templateUrl: './grammar-page.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class GrammarPage {
  private readonly api = inject(GrammarApi);

  protected readonly levels = CEFR_LEVELS;
  protected readonly level = signal<CefrLevel | null>(null);

  /** level đổi → resource tự gọi lại API với tham số mới. */
  protected readonly topics = rxResource({
    params: () => this.level(),
    stream: ({ params }) => this.api.listTopics(params),
  });
}
