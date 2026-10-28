import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { rxResource } from '@angular/core/rxjs-interop';
import { Router, RouterLink } from '@angular/router';

import { DeckApi } from '../../core/api/deck.api';
import { Deck } from '../../core/models/deck.models';
import { ToastService } from '../../core/ui/toast.service';
import { relativeDay } from '../../core/util/dates';
import { EmptyState } from '../../shared/ui/empty-state/empty-state';
import { ErrorState } from '../../shared/ui/error-state/error-state';
import { Icon } from '../../shared/ui/icon/icon';
import { Skeleton } from '../../shared/ui/skeleton/skeleton';
import { DeckFormDialog } from './deck-form-dialog/deck-form-dialog';

type DeckFilter = 'all' | 'mine' | 'system';

/** Trang quản lý bộ từ: nền giấy trơn (không ô li) vì đây là trang thao tác, không phải trang học. */
@Component({
  selector: 'app-decks-page',
  imports: [RouterLink, DeckFormDialog, EmptyState, ErrorState, Icon, Skeleton],
  templateUrl: './decks-page.html',
  styleUrl: './decks-page.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class DecksPage {
  private readonly api = inject(DeckApi);
  private readonly router = inject(Router);
  private readonly toast = inject(ToastService);

  protected readonly decks = rxResource({ stream: () => this.api.listDecks() });
  protected readonly filter = signal<DeckFilter>('all');
  protected readonly query = signal('');
  protected readonly creating = signal(false);
  protected readonly relativeDay = relativeDay;

  protected readonly filters: { value: DeckFilter; label: string }[] = [
    { value: 'all', label: 'Tất cả' },
    { value: 'mine', label: 'Của tôi' },
    { value: 'system', label: 'Có sẵn' },
  ];

  /** Lọc + tìm kiếm ngay trên trình duyệt: số bộ của một người không lớn, không cần gọi API. */
  protected readonly visible = computed(() => {
    if (!this.decks.hasValue()) {
      return [];
    }
    const q = this.query().trim().toLowerCase();
    return this.decks.value().filter((d) => {
      const byType = this.filter() === 'all' || (this.filter() === 'mine') === (d.deckType === 'USER_DECK');
      return byType && (!q || d.deckName.toLowerCase().includes(q));
    });
  });

  protected readonly summary = computed(() => {
    if (!this.decks.hasValue()) {
      return '';
    }
    const list = this.decks.value();
    const cards = list.reduce((sum, d) => sum + d.cardCount, 0);
    const due = list.reduce((sum, d) => sum + (d.dueCount ?? 0), 0);
    return `${list.length} bộ, ${cards} thẻ.` + (due > 0 ? ` ${due} thẻ đến hạn hôm nay.` : ' Hôm nay đã ôn xong.');
  });

  protected onQuery(event: Event): void {
    this.query.set((event.target as HTMLInputElement).value);
  }

  protected onCreated(deck: Deck): void {
    this.creating.set(false);
    this.toast.success(`Đã tạo bộ "${deck.deckName}"`);
    void this.router.navigate(['/decks', deck.id]);
  }
}
