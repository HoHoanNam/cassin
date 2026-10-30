import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { rxResource } from '@angular/core/rxjs-interop';
import { Router, RouterLink } from '@angular/router';
import { map } from 'rxjs';

import { AdminApi } from '../../core/api/admin.api';
import { DeckApi } from '../../core/api/deck.api';
import { errorMessage } from '../../core/models/common.models';
import { Deck } from '../../core/models/deck.models';
import { ToastService } from '../../core/ui/toast.service';
import { relativeDay } from '../../core/util/dates';
import { ConfirmDialog } from '../../shared/ui/confirm-dialog/confirm-dialog';
import { EmptyState } from '../../shared/ui/empty-state/empty-state';
import { ErrorState } from '../../shared/ui/error-state/error-state';
import { Icon } from '../../shared/ui/icon/icon';
import { Skeleton } from '../../shared/ui/skeleton/skeleton';
import { DeckFormDialog } from '../decks/deck-form-dialog/deck-form-dialog';

/**
 * Danh sách bộ từ HỆ THỐNG.
 * Đọc: dùng lại GET /api/decks (bộ hệ thống ai cũng xem được) rồi lọc deckType.
 * Ghi: /api/admin/decks (qua DeckFormDialog scope="system" và AdminApi.deleteDeck).
 */
@Component({
  selector: 'app-admin-decks-page',
  imports: [RouterLink, ConfirmDialog, DeckFormDialog, EmptyState, ErrorState, Icon, Skeleton],
  templateUrl: './admin-decks-page.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class AdminDecksPage {
  private readonly deckApi = inject(DeckApi);
  private readonly adminApi = inject(AdminApi);
  private readonly router = inject(Router);
  private readonly toast = inject(ToastService);

  protected readonly relativeDay = relativeDay;

  protected readonly decks = rxResource({
    stream: () => this.deckApi.listDecks().pipe(map((list) => list.filter((d) => d.deckType === 'SYSTEM_DECK'))),
  });

  protected readonly creating = signal(false);
  protected readonly editing = signal<Deck | null>(null);
  protected readonly toDelete = signal<Deck | null>(null);
  protected readonly deleting = signal(false);

  protected onCreated(deck: Deck): void {
    this.creating.set(false);
    this.toast.success(`Đã tạo bộ "${deck.deckName}". Thêm thẻ cho bộ ngay bây giờ.`);
    void this.router.navigate(['/admin/decks', deck.id]);
  }

  protected onEdited(deck: Deck): void {
    this.editing.set(null);
    this.toast.success(`Đã lưu bộ "${deck.deckName}"`);
    this.decks.reload();
  }

  protected deleteDeck(): void {
    const deck = this.toDelete();
    if (!deck) {
      return;
    }
    this.deleting.set(true);
    this.adminApi.deleteDeck(deck.id).subscribe({
      next: () => {
        this.deleting.set(false);
        this.toDelete.set(null);
        this.toast.success(`Đã xóa bộ "${deck.deckName}"`);
        this.decks.reload();
      },
      error: (error: unknown) => {
        // 409 DECK_IN_USE: backend đã ghi sẵn câu dễ hiểu ("Bộ từ đã có người học...").
        this.deleting.set(false);
        this.toDelete.set(null);
        this.toast.error(errorMessage(error));
      },
    });
  }
}
