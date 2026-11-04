import { ChangeDetectionStrategy, Component, computed, inject, input, numberAttribute, signal } from '@angular/core';
import { rxResource } from '@angular/core/rxjs-interop';
import { Router, RouterLink } from '@angular/router';
import { Observable } from 'rxjs';

import { AdminApi } from '../../core/api/admin.api';
import { DeckApi } from '../../core/api/deck.api';
import { ApiError, errorMessage } from '../../core/models/common.models';
import { Deck, Word, WordSummary } from '../../core/models/deck.models';
import { ToastService } from '../../core/ui/toast.service';
import { ConfirmDialog } from '../../shared/ui/confirm-dialog/confirm-dialog';
import { EmptyState } from '../../shared/ui/empty-state/empty-state';
import { ErrorState } from '../../shared/ui/error-state/error-state';
import { Icon } from '../../shared/ui/icon/icon';
import { Pager } from '../../shared/ui/pager/pager';
import { Skeleton } from '../../shared/ui/skeleton/skeleton';
import { DeckFormDialog } from '../decks/deck-form-dialog/deck-form-dialog';
import { WordFormDialog } from '../decks/word-form-dialog/word-form-dialog';
import { WordList } from '../decks/word-list/word-list';

const PAGE_SIZE = 20;

/**
 * Quản lý MỘT bộ từ hệ thống: sửa bộ, thêm / sửa từ hệ thống, bỏ từ khỏi bộ, xóa hẳn từ.
 * Đọc dùng DeckApi (GET /api/decks/{id}, /words), ghi dùng AdminApi (scope="system").
 * [V10] Một từ hệ thống nằm được trong nhiều bộ: "Bỏ khỏi bộ" khác "Xóa từ".
 */
@Component({
  selector: 'app-admin-deck-page',
  imports: [RouterLink, ConfirmDialog, DeckFormDialog, EmptyState, ErrorState, Icon, Pager, Skeleton, WordFormDialog, WordList],
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

  protected readonly page = signal(0);

  protected readonly deck = rxResource({
    params: () => this.deckId(),
    stream: ({ params }) => this.deckApi.getDeck(params),
  });

  protected readonly words = rxResource({
    params: () => ({ deckId: this.deckId(), page: this.page() }),
    stream: ({ params }) => this.deckApi.listWords(params.deckId, '', params.page, PAGE_SIZE),
  });

  /** 404, hoặc bộ tồn tại nhưng là bộ CÁ NHÂN của ai đó: admin không quản lý ở đây. */
  protected readonly notManageable = computed(() => {
    const error = this.deck.error();
    if (error instanceof ApiError && error.status === 404) {
      return true;
    }
    return this.deck.hasValue() && this.deck.value().deckType !== 'SYSTEM_DECK';
  });

  protected readonly wordDialogOpen = signal(false);
  protected readonly editingWordId = signal<number | null>(null);
  protected readonly editingDeck = signal(false);
  protected readonly wordToRemove = signal<WordSummary | null>(null);
  protected readonly wordToDelete = signal<WordSummary | null>(null);
  protected readonly confirmDeleteDeck = signal(false);
  protected readonly busy = signal(false);

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

  protected onDeckSaved(deck: Deck): void {
    this.editingDeck.set(false);
    this.toast.success(`Đã lưu bộ "${deck.deckName}"`);
    this.deck.reload();
  }

  /** Bỏ khỏi bộ: từ hệ thống vẫn còn, có thể nằm trong bộ khác. */
  protected removeWord(): void {
    const word = this.wordToRemove();
    if (word) {
      this.run(this.adminApi.removeWord(this.deckId(), word.id), `Đã bỏ "${word.term}" khỏi bộ`, () => this.wordToRemove.set(null));
    }
  }

  /** Xóa hẳn từ hệ thống: mất luôn tiến độ của mọi học viên với từ này. */
  protected deleteWord(): void {
    const word = this.wordToDelete();
    if (word) {
      this.run(this.adminApi.deleteWord(word.id), `Đã xóa từ "${word.term}"`, () => this.wordToDelete.set(null));
    }
  }

  protected deleteDeck(): void {
    const deck = this.deck.value();
    this.busy.set(true);
    this.adminApi.deleteDeck(this.deckId()).subscribe({
      next: () => {
        this.busy.set(false);
        this.toast.success(`Đã xóa bộ "${deck?.deckName}"`);
        void this.router.navigateByUrl('/admin/decks');
      },
      error: (error: unknown) => {
        this.busy.set(false);
        this.confirmDeleteDeck.set(false);
        this.toast.error(errorMessage(error));
      },
    });
  }

  /** Chạy một thao tác bỏ / xóa từ, rồi tải lại danh sách (lùi trang nếu vừa xóa dòng cuối). */
  private run(call: Observable<void>, success: string, done: () => void): void {
    this.busy.set(true);
    call.subscribe({
      next: () => {
        this.busy.set(false);
        done();
        this.toast.success(success);
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
}
