import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { rxResource } from '@angular/core/rxjs-interop';
import { Router, RouterLink } from '@angular/router';

import { DeckApi } from '../../core/api/deck.api';
import { PracticeApi } from '../../core/api/practice.api';
import { Deck } from '../../core/models/deck.models';
import {
  ALL_SCOPE,
  PRACTICE_LIMITS,
  PRACTICE_MODE_LABEL,
  PracticeMode,
  PracticeScope,
} from '../../core/models/practice.models';
import { countProblem, maxCount, scopeToQuery, toggleDeck, toggleNotebook } from '../../core/util/practice-scope';
import { ErrorState } from '../../shared/ui/error-state/error-state';
import { Icon, IconName } from '../../shared/ui/icon/icon';
import { Skeleton } from '../../shared/ui/skeleton/skeleton';

/** Chế độ trên URL /practice/session?mode= (chữ thường, gạch nối cho dễ đọc) */
export type PracticeModeParam = 'fill-blank' | 'sentence-build';

interface ModeOption {
  param: PracticeModeParam;
  title: string;
  icon: IconName;
  description: string;
  example: string;
}

const STATS_DAYS = 7;
const WEEKDAY = ['CN', 'T2', 'T3', 'T4', 'T5', 'T6', 'T7'];

/**
 * [THÊM] Luyện tập theo câu ví dụ: chọn chế độ, phạm vi, số câu, rồi vào chế độ tập trung.
 * Kèm thống kê 7 ngày (GET /api/practice/stats) và lối vào Sổ lỗi sai.
 *
 * [SỬA] Phạm vi gộp được nhiều nguồn: "Tất cả" (mọi bộ hệ thống + sổ từ) / sổ từ / nhiều bộ.
 * [SỬA] Số câu: thanh trượt + ô số + nút MAX thay cho 10 / 20 cố định. Số câu tối đa lấy từ
 *       GET /api/practice/availability mỗi khi đổi phạm vi; nhập quá thì cảnh báo ngay và khóa nút Bắt đầu.
 */
@Component({
  selector: 'app-practice-page',
  imports: [RouterLink, ErrorState, Icon, Skeleton],
  templateUrl: './practice-page.html',
  styleUrl: './practice-page.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class PracticePage {
  private readonly practiceApi = inject(PracticeApi);
  private readonly deckApi = inject(DeckApi);
  private readonly router = inject(Router);

  protected readonly modes: ModeOption[] = [
    {
      param: 'fill-blank',
      title: 'Điền từ',
      icon: 'type',
      description: 'Đọc câu ví dụ đã che từ, gõ lại đúng dạng của từ trong câu.',
      example: 'Her English has ____ a lot.',
    },
    {
      param: 'sentence-build',
      title: 'Xếp câu',
      icon: 'shuffle',
      description: 'Các mảnh của câu ví dụ bị xáo trộn, xếp lại cho đúng thứ tự.',
      example: 'a lot. · Her · improved · English · has',
    },
  ];
  protected readonly modeLabel = PRACTICE_MODE_LABEL;

  protected readonly mode = signal<PracticeModeParam>('fill-blank');
  /** [SỬA] deckId → scope. Mặc định "Tất cả" để người mới (sổ từ trống) vẫn có câu luyện. */
  protected readonly scope = signal<PracticeScope>(ALL_SCOPE);
  /** null = ô số đang trống */
  protected readonly count = signal<number | null>(10);

  protected readonly decks = rxResource({ stream: () => this.deckApi.listDecks() });
  /** [THÊM] Đếm lại số câu có sẵn mỗi khi đổi phạm vi (đổi chế độ thì không cần: một lần trả cả 2 chế độ). */
  protected readonly availability = rxResource({
    params: () => this.scope(),
    stream: ({ params }) => this.practiceApi.availability(params),
  });
  protected readonly stats = rxResource({ stream: () => this.practiceApi.stats(STATS_DAYS) });
  protected readonly mistakes = rxResource({ stream: () => this.practiceApi.mistakes(STATS_DAYS, 50) });

  protected readonly accuracyPercent = computed(() =>
    this.stats.hasValue() ? Math.round(this.stats.value().accuracy * 100) : 0,
  );
  /** Cột cao nhất của biểu đồ 7 ngày (để chia tỉ lệ), tối thiểu 1 để không chia cho 0. */
  protected readonly maxDaily = computed(() =>
    this.stats.hasValue() ? Math.max(1, ...this.stats.value().daily.map((d) => d.total)) : 1,
  );

  /** Bộ hệ thống trước, rồi bộ của tôi; bỏ bộ rỗng (không có gì để luyện). */
  protected readonly sourceDecks = computed<Deck[]>(() =>
    this.decks.hasValue()
      ? [...this.decks.value()]
          .filter((d) => d.wordCount > 0)
          .sort((a, b) => Number(a.deckType !== 'SYSTEM_DECK') - Number(b.deckType !== 'SYSTEM_DECK'))
      : [],
  );

  /** Số câu có sẵn cho chế độ đang chọn (0 khi chưa tải xong). */
  protected readonly available = computed(() => {
    if (!this.availability.hasValue()) {
      return 0;
    }
    const a = this.availability.value();
    return this.mode() === 'sentence-build' ? a.sentenceBuild : a.fillBlank;
  });
  protected readonly cap = computed(() =>
    this.availability.hasValue() ? this.availability.value().maxQuestions : PRACTICE_LIMITS.maxQuestions,
  );
  protected readonly max = computed(() => maxCount(this.available(), this.cap()));
  /** Cảnh báo dưới ô số câu. Chưa đếm xong thì chưa cảnh báo. */
  protected readonly problem = computed(() =>
    this.availability.hasValue() ? countProblem(this.count(), this.available(), this.cap()) : null,
  );
  protected readonly canStart = computed(() => this.availability.hasValue() && this.problem() === null);

  protected readonly scopeSummary = computed(() => {
    const scope = this.scope();
    if (scope.all) {
      return 'Mọi bộ hệ thống + sổ từ của bạn';
    }
    const parts = [];
    if (scope.deckIds.length > 0) {
      parts.push(`${scope.deckIds.length} bộ`);
    }
    if (scope.notebook) {
      parts.push('sổ từ');
    }
    return `Đang gộp ${parts.join(' + ')}`;
  });

  protected isDeckSelected(deckId: number): boolean {
    const scope = this.scope();
    return !scope.all && scope.deckIds.includes(deckId);
  }

  protected selectAll(): void {
    this.scope.set(ALL_SCOPE);
  }

  protected onToggleNotebook(): void {
    this.scope.update(toggleNotebook);
  }

  protected onToggleDeck(deckId: number): void {
    this.scope.update((scope) => toggleDeck(scope, deckId));
  }

  /** Ô số: để trống được (null) để người học xóa rồi gõ lại; cảnh báo lo phần còn lại. */
  protected onCountInput(event: Event): void {
    const raw = (event.target as HTMLInputElement).value;
    this.count.set(raw === '' ? null : Number(raw));
  }

  protected useMax(): void {
    this.count.set(this.max());
  }

  protected weekday(isoDate: string): string {
    return WEEKDAY[new Date(`${isoDate}T00:00:00`).getDay()];
  }

  protected modeStat(mode: PracticeMode): string {
    const stat = this.stats.hasValue() ? this.stats.value().byMode.find((m) => m.mode === mode) : undefined;
    return stat ? `${stat.correct}/${stat.total} câu đúng` : 'Chưa luyện';
  }

  protected start(): void {
    if (!this.canStart()) {
      return;
    }
    void this.router.navigate(['/practice/session'], {
      queryParams: { mode: this.mode(), ...scopeToQuery(this.scope()), count: this.count() },
    });
  }
}
