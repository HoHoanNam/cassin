import { ChangeDetectionStrategy, Component, computed, inject, input, numberAttribute, signal } from '@angular/core';
import { rxResource } from '@angular/core/rxjs-interop';
import { Router, RouterLink } from '@angular/router';

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
import { CardFormDialog } from './card-form-dialog/card-form-dialog';
import { DeckFormDialog } from './deck-form-dialog/deck-form-dialog';

const PAGE_SIZE = 20;

/** Chi tiết một bộ từ: thông tin bộ, danh sách thẻ (phân trang), thêm / sửa / xóa thẻ. */
@Component({
  selector: 'app-deck-detail-page',
  imports: [RouterLink, CardFormDialog, ConfirmDialog, DeckFormDialog, EmptyState, ErrorState, Icon, Pager, Skeleton],
  templateUrl: './deck-detail-page.html',
  styleUrl: './deck-detail-page.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class DeckDetailPage {
  /** :deckId trên URL, đổi sang số nhờ numberAttribute. */
  readonly deckId = input.required({ transform: numberAttribute });

  private readonly api = inject(DeckApi);
  private readonly router = inject(Router);
  private readonly toast = inject(ToastService);

  protected readonly pos = PART_OF_SPEECH;
  protected readonly page = signal(0);

  protected readonly deck = rxResource({
    params: () => this.deckId(),
    stream: ({ params }) => this.api.getDeck(params),
  });

  protected readonly cards = rxResource({
    params: () => ({ deckId: this.deckId(), page: this.page() }),
    stream: ({ params }) => this.api.listCards(params.deckId, params.page, PAGE_SIZE),
  });

  protected readonly notFound = computed(() => {
    const error = this.deck.error();
    return error instanceof ApiError && error.status === 404;
  });

  // ---- Trạng thái hộp thoại ----
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
    this.api.deleteCard(card.id).subscribe({
      next: () => {
        this.deleting.set(false);
        this.cardToDelete.set(null);
        this.toast.success(`Đã xóa thẻ "${card.word}"`);
        // Xóa thẻ cuối cùng của trang cuối → lùi về trang trước.
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
    this.api.deleteDeck(this.deckId()).subscribe({
      next: () => {
        this.deleting.set(false);
        this.toast.success(`Đã xóa bộ "${deck?.deckName}"`);
        void this.router.navigateByUrl('/decks');
      },
      error: (error: unknown) => {
        this.deleting.set(false);
        this.confirmDeleteDeck.set(false);
        this.toast.error(errorMessage(error));
      },
    });
  }

  protected goToPage(page: number): void {
    this.page.set(page);
  }
}
