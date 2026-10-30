import { ChangeDetectionStrategy, Component, computed, inject, input, numberAttribute, signal } from '@angular/core';
import { rxResource } from '@angular/core/rxjs-interop';
import { Router, RouterLink } from '@angular/router';

import { AdminApi } from '../../core/api/admin.api';
import { DeckApi } from '../../core/api/deck.api';
import { ApiError, errorMessage } from '../../core/models/common.models';
import { Card, Deck } from '../../core/models/deck.models';
import { ToastService } from '../../core/ui/toast.service';
import { PART_OF_SPEECH } from '../../core/util/part-of-speech';
import { ConfirmDialog } from '../../shared/ui/confirm-dialog/confirm-dialog';
import { EmptyState } from '../../shared/ui/empty-state/empty-state';
import { ErrorState } from '../../shared/ui/error-state/error-state';
import { Icon } from '../../shared/ui/icon/icon';
import { Pager } from '../../shared/ui/pager/pager';
import { Skeleton } from '../../shared/ui/skeleton/skeleton';
import { CardFormDialog } from '../decks/card-form-dialog/card-form-dialog';
import { DeckFormDialog } from '../decks/deck-form-dialog/deck-form-dialog';

const PAGE_SIZE = 20;

/**
 * Quản lý MỘT bộ từ hệ thống: sửa bộ, thêm / sửa / xóa thẻ.
 * Đọc dùng DeckApi (GET /api/decks/{id}, /cards), ghi dùng AdminApi qua các hộp thoại scope="system".
 */
@Component({
  selector: 'app-admin-deck-page',
  imports: [RouterLink, CardFormDialog, ConfirmDialog, DeckFormDialog, EmptyState, ErrorState, Icon, Pager, Skeleton],
  templateUrl: './admin-deck-page.html',
  styleUrl: './admin-deck-page.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class AdminDeckPage {
  readonly deckId = input.required({ transform: numberAttribute });

  private readonly deckApi = inject(DeckApi);
  private readonly adminApi = inject(AdminApi);
  private readonly router = inject(Router);
  private readonly toast = inject(ToastService);

  protected readonly pos = PART_OF_SPEECH;
  protected readonly page = signal(0);

  protected readonly deck = rxResource({
    params: () => this.deckId(),
    stream: ({ params }) => this.deckApi.getDeck(params),
  });

  protected readonly cards = rxResource({
    params: () => ({ deckId: this.deckId(), page: this.page() }),
    stream: ({ params }) => this.deckApi.listCards(params.deckId, params.page, PAGE_SIZE),
  });

  /** 404, hoặc bộ tồn tại nhưng là bộ CÁ NHÂN của ai đó: admin không quản lý ở đây. */
  protected readonly notManageable = computed(() => {
    const error = this.deck.error();
    if (error instanceof ApiError && error.status === 404) {
      return true;
    }
    return this.deck.hasValue() && this.deck.value().deckType !== 'SYSTEM_DECK';
  });

  protected readonly cardDialogOpen = signal(false);
  protected readonly editingCard = signal<Card | null>(null);
  protected readonly editingDeck = signal(false);
  protected readonly cardToDelete = signal<Card | null>(null);
  protected readonly confirmDeleteDeck = signal(false);
  protected readonly deleting = signal(false);

  protected openAddCard(): void {
    this.editingCard.set(null);
    this.cardDialogOpen.set(true);
  }

  protected openEditCard(card: Card): void {
    this.editingCard.set(card);
    this.cardDialogOpen.set(true);
  }

  protected onCardSaved(event: { card: Card; keepOpen: boolean }): void {
    const isEdit = this.editingCard() !== null;
    this.toast.success(isEdit ? `Đã lưu thẻ "${event.card.word}"` : `Đã thêm thẻ "${event.card.word}"`);
    if (!event.keepOpen) {
      this.cardDialogOpen.set(false);
    }
    this.cards.reload();
    this.deck.reload();
  }

  protected onDeckSaved(deck: Deck): void {
    this.editingDeck.set(false);
    this.toast.success(`Đã lưu bộ "${deck.deckName}"`);
    this.deck.reload();
  }

  protected deleteCard(): void {
    const card = this.cardToDelete();
    if (!card) {
      return;
    }
    this.deleting.set(true);
    this.adminApi.deleteCard(card.id).subscribe({
      next: () => {
        this.deleting.set(false);
        this.cardToDelete.set(null);
        this.toast.success(`Đã xóa thẻ "${card.word}"`);
        if (this.cards.hasValue() && this.cards.value().content.length === 1 && this.page() > 0) {
          this.page.update((p) => p - 1);
        } else {
          this.cards.reload();
        }
        this.deck.reload();
      },
      error: (error: unknown) => {
        this.deleting.set(false);
        this.toast.error(errorMessage(error));
      },
    });
  }

  protected deleteDeck(): void {
    const deck = this.deck.value();
    this.deleting.set(true);
    this.adminApi.deleteDeck(this.deckId()).subscribe({
      next: () => {
        this.deleting.set(false);
        this.toast.success(`Đã xóa bộ "${deck?.deckName}"`);
        void this.router.navigateByUrl('/admin/decks');
      },
      error: (error: unknown) => {
        this.deleting.set(false);
        this.confirmDeleteDeck.set(false);
        this.toast.error(errorMessage(error));
      },
    });
  }
}
