import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { rxResource } from '@angular/core/rxjs-interop';
import { RouterLink } from '@angular/router';

import { QuizApi } from '../../core/api/quiz.api';
import { relativeDay } from '../../core/util/dates';
import { EmptyState } from '../../shared/ui/empty-state/empty-state';
import { ErrorState } from '../../shared/ui/error-state/error-state';
import { Pager } from '../../shared/ui/pager/pager';
import { ScoreStamp } from '../../shared/ui/score-stamp/score-stamp';
import { Skeleton } from '../../shared/ui/skeleton/skeleton';

const PAGE_SIZE = 10;

/** Lịch sử làm bài của tôi, mới nhất trước, có phân trang. */
@Component({
  selector: 'app-quiz-history-page',
  imports: [RouterLink, EmptyState, ErrorState, Pager, ScoreStamp, Skeleton],
  templateUrl: './quiz-history-page.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class QuizHistoryPage {
  private readonly api = inject(QuizApi);

  protected readonly page = signal(0);
  protected readonly relativeDay = relativeDay;
  protected readonly history = rxResource({
    params: () => this.page(),
    stream: ({ params }) => this.api.history(params, PAGE_SIZE),
  });
}
