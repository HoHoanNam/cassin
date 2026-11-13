import { ChangeDetectionStrategy, Component, computed, inject, input, numberAttribute, signal } from '@angular/core';
import { rxResource } from '@angular/core/rxjs-interop';
import { Router, RouterLink } from '@angular/router';

import { DeckApi } from '../../core/api/deck.api';
import { ApiError, errorMessage } from '../../core/models/common.models';
import { Deck, Word, WordSummary } from '../../core/models/deck.models';
import { ImportResult } from '../../core/models/import.models';
import { ToastService } from '../../core/ui/toast.service';
import { ConfirmDialog } from '../../shared/ui/confirm-dialog/confirm-dialog';
import { EmptyState } from '../../shared/ui/empty-state/empty-state';
import { ErrorState } from '../../shared/ui/error-state/error-state';
import { Icon } from '../../shared/ui/icon/icon';
import { Pager } from '../../shared/ui/pager/pager';
import { Skeleton } from '../../shared/ui/skeleton/skeleton';
import { DeckFormDialog } from './deck-form-dialog/deck-form-dialog';
import { ImportDialog } from './import-dialog/import-dialog';
import { WordFormDialog } from './word-form-dialog/word-form-dialog';
import { WordList } from './word-list/word-list';

const PAGE_SIZE = 20;

/**
 * Chi tiết một bộ từ: thông tin bộ, danh sách từ (tìm kiếm + phân trang), thêm / sửa từ.
 * [V10] Bộ là NHÃN chứa từ: "Bỏ khỏi bộ" chỉ gỡ nhãn, từ và tiến độ ôn vẫn còn trong sổ từ.
 */
@Component({
  selector: 'app-deck-detail-page',
  imports: [RouterLink, ConfirmDialog, DeckFormDialog, EmptyState, ImportDialog, ErrorState, Icon, Pager, Skeleton, WordFormDialog, WordList],
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

  protected readonly page = signal(0);
  protected readonly query = signal('');

  protected readonly deck = rxResource({
    params: () => this.deckId(),
    stream: ({ params }) => this.api.getDeck(params),
  });

  protected readonly words = rxResource({
    params: () => ({ deckId: this.deckId(), q: this.query(), page: this.page() }),
    stream: ({ params }) => this.api.listWords(params.deckId, params.q, params.page, PAGE_SIZE),
  });

  protected readonly notFound = computed(() => {
    const error = this.deck.error();
    return error instanceof ApiError && error.status === 404;
  });

  // ---- Trạng thái hộp thoại ----
  protected readonly wordDialogOpen = signal(false);
  /** null = thêm từ mới; có id = sửa từ đó */
  protected readonly editingWordId = signal<number | null>(null);
  protected readonly editingDeck = signal(false);
  protected readonly wordToRemove = signal<WordSummary | null>(null);
  protected readonly confirmDeleteDeck = signal(false);
  protected readonly busy = signal(false);
  /** Hộp thoại nhập CSV */
  protected readonly importOpen = signal(false);

  protected openAddWord(): void {
    this.editingWordId.set(null);
    this.wordDialogOpen.set(true);
  }

  protected openEditWord(word: WordSummary): void {
    this.editingWordId.set(word.id);
    this.wordDialogOpen.set(true);
  }

  protected onWordSaved(event: { word: Word; created: boolean; keepOpen: boolean }): void {
    this.toast.success(event.created ? `Đã thêm từ "${event.word.term}"` : `Đã lưu từ "${event.word.term}"`);
    if (!event.keepOpen) {
      this.wordDialogOpen.set(false);
    }
    this.words.reload();
    this.deck.reload();
  }

  /** Nhập CSV xong: hộp thoại vẫn mở (bước "Xong"), tải lại danh sách + số liệu của bộ ngay. */
  protected onImported(result: ImportResult): void {
    this.toast.success(`Đã nhập ${result.words} từ (${result.senses} nghĩa)`);
    this.page.set(0);
    this.words.reload();
    this.deck.reload();
  }

  protected onDeckSaved(deck: Deck): void {
    this.editingDeck.set(false);
    this.toast.success(`Đã lưu bộ "${deck.deckName}"`);
    this.deck.reload();
  }

  protected onSearch(event: Event): void {
    this.query.set((event.target as HTMLInputElement).value.trim());
    this.page.set(0);
  }

  /** Bỏ từ khỏi bộ: chỉ xóa liên kết deck_words, từ vẫn còn trong sổ từ. */
  protected removeWord(): void {
    const word = this.wordToRemove();
    if (!word) {
      return;
    }
    this.busy.set(true);
    this.api.removeWord(this.deckId(), word.id).subscribe({
      next: () => {
        this.busy.set(false);
        this.wordToRemove.set(null);
        this.toast.success(`Đã bỏ "${word.term}" khỏi bộ`);
        // Bỏ từ cuối cùng của trang cuối → lùi về trang trước.
        if (this.words.hasValue() && this.words.value().content.length === 1 && this.page() > 0) {
          this.page.update((p) => p - 1);
        } else {
          this.words.reload();
        }
        this.deck.reload();
      },
      error: (error: unknown) => {
        this.busy.set(false);
        this.toast.error(errorMessage(error));
      },
    });
  }

  protected deleteDeck(): void {
    const deck = this.deck.value();
    this.busy.set(true);
    this.api.deleteDeck(this.deckId()).subscribe({
      next: () => {
        this.busy.set(false);
        this.toast.success(`Đã xóa bộ "${deck?.deckName}"`);
        void this.router.navigateByUrl('/decks');
      },
      error: (error: unknown) => {
        this.busy.set(false);
        this.confirmDeleteDeck.set(false);
        this.toast.error(errorMessage(error));
      },
    });
  }

  protected goToPage(page: number): void {
    this.page.set(page);
  }
}
