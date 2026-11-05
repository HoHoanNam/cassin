import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { rxResource } from '@angular/core/rxjs-interop';
import { RouterLink } from '@angular/router';

import { DeckApi } from '../../core/api/deck.api';
import { LibraryApi } from '../../core/api/library.api';
import { errorMessage } from '../../core/models/common.models';
import { LibraryWord } from '../../core/models/deck.models';
import { ToastService } from '../../core/ui/toast.service';
import { PART_OF_SPEECH } from '../../core/util/part-of-speech';
import { EmptyState } from '../../shared/ui/empty-state/empty-state';
import { ErrorState } from '../../shared/ui/error-state/error-state';
import { Icon } from '../../shared/ui/icon/icon';
import { Modal } from '../../shared/ui/modal/modal';
import { Pager } from '../../shared/ui/pager/pager';
import { Skeleton } from '../../shared/ui/skeleton/skeleton';

const PAGE_SIZE = 20;

/**
 * Thư viện từ: duyệt mọi TỪ HỆ THỐNG, xem từ nào mình đã có, rồi đặt từ vào một bộ của mình.
 *   Đọc : GET /api/library?q=&onlyNew=&page=
 *   Ghi : POST /api/decks/{deckId}/words/{wordId} (DeckApi.addWord, có sẵn từ V10)
 */
@Component({
  selector: 'app-library-page',
  imports: [RouterLink, EmptyState, ErrorState, Icon, Modal, Pager, Skeleton],
  templateUrl: './library-page.html',
  styleUrl: './library-page.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class LibraryPage {
  private readonly libraryApi = inject(LibraryApi);
  private readonly deckApi = inject(DeckApi);
  private readonly toast = inject(ToastService);

  protected readonly pos = PART_OF_SPEECH;
  protected readonly query = signal('');
  protected readonly onlyNew = signal(false);
  protected readonly page = signal(0);

  protected readonly words = rxResource({
    params: () => ({ q: this.query(), onlyNew: this.onlyNew(), page: this.page() }),
    stream: ({ params }) => this.libraryApi.search(params.q, params.onlyNew, params.page, PAGE_SIZE),
  });

  /** Bộ CỦA TÔI (sửa được) để chọn khi "Thêm vào bộ". Tải một lần. */
  protected readonly decks = rxResource({ stream: () => this.deckApi.listDecks() });
  protected readonly myDecks = computed(() => (this.decks.hasValue() ? this.decks.value().filter((d) => d.editable) : []));

  // ---- Hộp thoại "Thêm vào bộ" ----
  protected readonly adding = signal<LibraryWord | null>(null);
  protected readonly selectedDeckId = signal<number | null>(null);
  protected readonly saving = signal(false);

  protected onSearch(event: Event): void {
    this.query.set((event.target as HTMLInputElement).value.trim());
    this.page.set(0);
  }

  protected toggleOnlyNew(event: Event): void {
    this.onlyNew.set((event.target as HTMLInputElement).checked);
    this.page.set(0);
  }

  protected openAdd(word: LibraryWord): void {
    this.adding.set(word);
    // Chọn sẵn bộ đầu tiên: phần lớn người dùng chỉ có 1–2 bộ
    this.selectedDeckId.set(this.myDecks()[0]?.id ?? null);
  }

  protected closeAdd(): void {
    if (!this.saving()) {
      this.adding.set(null);
    }
  }

  protected confirmAdd(): void {
    const word = this.adding();
    const deckId = this.selectedDeckId();
    const deck = this.myDecks().find((d) => d.id === deckId);
    if (!word || !deck) {
      return;
    }
    this.saving.set(true);
    this.deckApi.addWord(deck.id, word.id).subscribe({
      next: () => {
        this.saving.set(false);
        this.adding.set(null);
        this.toast.success(`Đã thêm "${word.term}" vào bộ "${deck.deckName}"`);
        // Cờ inMyDecks đổi → tải lại trang (với onlyNew, từ vừa thêm sẽ biến khỏi danh sách)
        this.words.reload();
        this.decks.reload();
      },
      error: (error: unknown) => {
        this.saving.set(false);
        this.toast.error(errorMessage(error));
      },
    });
  }
}
