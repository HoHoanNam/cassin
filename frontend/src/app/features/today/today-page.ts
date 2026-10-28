import { ChangeDetectionStrategy, Component, computed, inject } from '@angular/core';
import { rxResource } from '@angular/core/rxjs-interop';
import { RouterLink } from '@angular/router';

import { DeckApi } from '../../core/api/deck.api';
import { GrammarApi } from '../../core/api/grammar.api';
import { QuizApi } from '../../core/api/quiz.api';
import { AuthStore } from '../../core/auth/auth.store';
import { StudyStatsStore } from '../../core/state/study-stats.store';
import { longVietnameseDate, relativeDay } from '../../core/util/dates';
import { BoxChart } from '../../shared/ui/box-chart/box-chart';
import { EmptyState } from '../../shared/ui/empty-state/empty-state';
import { ErrorState } from '../../shared/ui/error-state/error-state';
import { Icon } from '../../shared/ui/icon/icon';
import { LevelBadge } from '../../shared/ui/level-badge/level-badge';
import { ScoreStamp } from '../../shared/ui/score-stamp/score-stamp';
import { Skeleton } from '../../shared/ui/skeleton/skeleton';

/**
 * Trang chính sau khi đăng nhập: việc cần làm HÔM NAY lên đầu (ôn thẻ đến hạn),
 * rồi tới bộ từ, ngữ pháp và bài kiểm tra gần đây.
 * Mỗi khối tự tải và tự báo lỗi riêng: một khối lỗi không làm hỏng cả trang.
 */
@Component({
  selector: 'app-today-page',
  imports: [RouterLink, BoxChart, EmptyState, ErrorState, Icon, LevelBadge, ScoreStamp, Skeleton],
  templateUrl: './today-page.html',
  styleUrl: './today-page.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class TodayPage {
  private readonly deckApi = inject(DeckApi);
  private readonly grammarApi = inject(GrammarApi);
  private readonly quizApi = inject(QuizApi);

  protected readonly user = inject(AuthStore).user;
  protected readonly statsStore = inject(StudyStatsStore);
  protected readonly stats = this.statsStore.stats;
  protected readonly today = longVietnameseDate();
  protected readonly relativeDay = relativeDay;

  protected readonly decks = rxResource({ stream: () => this.deckApi.listDecks() });
  protected readonly topics = rxResource({ stream: () => this.grammarApi.listTopics(null) });
  protected readonly history = rxResource({ stream: () => this.quizApi.history(0, 3) });

  /** Các bộ có thẻ đến hạn, nhiều nhất lên trước: "12 thẻ từ 1000 từ thông dụng và 6 thẻ từ ..." */
  protected readonly dueBreakdown = computed(() => {
    if (!this.decks.hasValue()) {
      return '';
    }
    const parts = this.decks
      .value()
      .filter((d) => (d.dueCount ?? 0) > 0)
      .sort((a, b) => (b.dueCount ?? 0) - (a.dueCount ?? 0))
      .map((d) => `${d.dueCount} thẻ từ "${d.deckName}"`);
    if (parts.length <= 1) {
      return parts.length === 1 ? parts[0] + '.' : '';
    }
    return parts.slice(0, -1).join(', ') + ' và ' + parts.at(-1) + '.';
  });

  /** Gợi ý học thẻ mới khi đã ôn xong: bộ còn nhiều thẻ mới nhất. */
  protected readonly suggestedNewDeck = computed(() => {
    if (!this.decks.hasValue()) {
      return null;
    }
    return [...this.decks.value()].filter((d) => (d.newCount ?? 0) > 0).sort((a, b) => (b.newCount ?? 0) - (a.newCount ?? 0))[0] ?? null;
  });

  protected readonly topDecks = computed(() => (this.decks.hasValue() ? this.decks.value().slice(0, 4) : []));
  protected readonly topTopics = computed(() =>
    this.topics.hasValue() ? this.topics.value().filter((t) => t.questionCount > 0).slice(0, 3) : [],
  );
}
