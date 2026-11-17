import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { rxResource } from '@angular/core/rxjs-interop';
import { Router, RouterLink } from '@angular/router';

import { DeckApi } from '../../core/api/deck.api';
import { PracticeApi } from '../../core/api/practice.api';
import { PRACTICE_MODE_LABEL, PracticeMode } from '../../core/models/practice.models';
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
 * [THÊM] Luyện tập theo câu ví dụ: chọn chế độ, phạm vi (cả sổ từ / một bộ), số câu, rồi vào chế độ tập trung.
 * Kèm thống kê 7 ngày (GET /api/practice/stats) và lối vào Sổ lỗi sai.
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
  protected readonly counts = [10, 20];
  protected readonly modeLabel = PRACTICE_MODE_LABEL;

  protected readonly mode = signal<PracticeModeParam>('fill-blank');
  /** null = cả sổ từ */
  protected readonly deckId = signal<number | null>(null);
  protected readonly count = signal(10);

  protected readonly decks = rxResource({ stream: () => this.deckApi.listDecks() });
  protected readonly stats = rxResource({ stream: () => this.practiceApi.stats(STATS_DAYS) });
  protected readonly mistakes = rxResource({ stream: () => this.practiceApi.mistakes(STATS_DAYS, 50) });

  protected readonly accuracyPercent = computed(() =>
    this.stats.hasValue() ? Math.round(this.stats.value().accuracy * 100) : 0,
  );
  /** Cột cao nhất của biểu đồ 7 ngày (để chia tỉ lệ), tối thiểu 1 để không chia cho 0. */
  protected readonly maxDaily = computed(() =>
    this.stats.hasValue() ? Math.max(1, ...this.stats.value().daily.map((d) => d.total)) : 1,
  );

  protected weekday(isoDate: string): string {
    return WEEKDAY[new Date(`${isoDate}T00:00:00`).getDay()];
  }

  protected modeStat(mode: PracticeMode): string {
    const stat = this.stats.hasValue() ? this.stats.value().byMode.find((m) => m.mode === mode) : undefined;
    return stat ? `${stat.correct}/${stat.total} câu đúng` : 'Chưa luyện';
  }

  protected onDeckChange(event: Event): void {
    const value = (event.target as HTMLSelectElement).value;
    this.deckId.set(value === '' ? null : Number(value));
  }

  protected start(): void {
    void this.router.navigate(['/practice/session'], {
      queryParams: { mode: this.mode(), deckId: this.deckId() ?? undefined, count: this.count() },
    });
  }
}
