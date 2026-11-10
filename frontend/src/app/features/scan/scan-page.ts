import { ChangeDetectionStrategy, Component, computed, effect, inject, signal } from '@angular/core';
import { rxResource } from '@angular/core/rxjs-interop';
import { RouterLink } from '@angular/router';
import { concatMap, from, map } from 'rxjs';

import { DeckApi } from '../../core/api/deck.api';
import { ScanApi } from '../../core/api/scan.api';
import { WordApi } from '../../core/api/word.api';
import { ApiError, errorMessage } from '../../core/models/common.models';
import { PartOfSpeech, WORD_LIMITS } from '../../core/models/deck.models';
import { SCAN_MAX_LENGTH, ScanItem, ScanResult, ScanSource } from '../../core/models/scan.models';
import { ToastService } from '../../core/ui/toast.service';
import { ExampleParts, parseExample } from '../../core/util/example-markup';
import { PART_OF_SPEECH, PART_OF_SPEECH_OPTIONS } from '../../core/util/part-of-speech';
import { EmptyState } from '../../shared/ui/empty-state/empty-state';
import { Icon } from '../../shared/ui/icon/icon';

/** Trạng thái của MỘT dòng kết quả trên màn hình (backend không biết gì về nó). */
type RowStatus = 'idle' | 'saving' | 'added' | 'skipped';

interface Row {
  item: ScanItem;
  example: ExampleParts | null;
  status: RowStatus;
  /** Bộ đã thêm vào (hiện "Đã thêm vào bộ X") */
  addedTo: string | null;
  /** Từ NEW: form nhập nghĩa đang mở */
  editing: boolean;
  term: string;
  partOfSpeech: PartOfSpeech;
  definition: string;
  error: string | null;
}

type Filter = 'ALL' | ScanSource;

const SAMPLE_TEXT =
  'Last summer, Mai decided to travel alone for the first time. At the airport she felt nervous, ' +
  'but a friendly stranger helped her break the ice with other travellers. She gave up checking her phone ' +
  'and started exploring narrow streets, tasting local dishes and bargaining at noisy markets. ' +
  'By the end of the trip, she had become more confident and independent.';

/**
 * Quét đoạn văn: dán đoạn tiếng Anh → các từ CHƯA có trong sổ từ → thêm vào một bộ hoặc bỏ qua.
 *   Quét : POST /api/scan                                   (ScanApi)
 *   Thêm : LIBRARY → POST /api/decks/{deckId}/words/{id}    (DeckApi.addWord, 1 chạm)
 *          NEW     → POST /api/words kèm deckIds            (WordApi.createWord, cần nhập nghĩa)
 */
@Component({
  selector: 'app-scan-page',
  imports: [RouterLink, EmptyState, Icon],
  templateUrl: './scan-page.html',
  styleUrl: './scan-page.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ScanPage {
  private readonly scanApi = inject(ScanApi);
  private readonly deckApi = inject(DeckApi);
  private readonly wordApi = inject(WordApi);
  private readonly toast = inject(ToastService);

  protected readonly maxLength = SCAN_MAX_LENGTH;
  protected readonly limits = WORD_LIMITS;
  protected readonly pos = PART_OF_SPEECH;
  protected readonly posOptions = PART_OF_SPEECH_OPTIONS;

  // ---- Ô nhập + kết quả ----
  protected readonly text = signal('');
  protected readonly scanning = signal(false);
  protected readonly scanError = signal<string | null>(null);
  protected readonly result = signal<ScanResult | null>(null);
  protected readonly rows = signal<Row[]>([]);
  protected readonly filter = signal<Filter>('ALL');

  protected readonly tooLong = computed(() => this.text().length > SCAN_MAX_LENGTH);
  protected readonly canScan = computed(() => this.text().trim().length > 0 && !this.tooLong() && !this.scanning());

  // ---- Bộ đích ----
  protected readonly decks = rxResource({ stream: () => this.deckApi.listDecks() });
  protected readonly myDecks = computed(() => (this.decks.hasValue() ? this.decks.value().filter((d) => d.editable) : []));
  protected readonly deckId = signal<number | null>(null);
  protected readonly deckName = computed(() => this.myDecks().find((d) => d.id === this.deckId())?.deckName ?? '');

  // ---- Số liệu hiển thị ----
  protected readonly visibleRows = computed(() => {
    const f = this.filter();
    return this.rows().filter((r) => f === 'ALL' || r.item.source === f);
  });
  protected readonly counts = computed(() => {
    const rows = this.rows();
    return {
      library: rows.filter((r) => r.item.source === 'LIBRARY').length,
      fresh: rows.filter((r) => r.item.source === 'NEW').length,
      added: rows.filter((r) => r.status === 'added').length,
      pendingLibrary: rows.filter((r) => r.item.source === 'LIBRARY' && r.status === 'idle').length,
    };
  });
  /** Tỉ lệ từ đã biết, cho thanh "Bạn đã biết x%" */
  protected readonly knownPercent = computed(() => {
    const r = this.result();
    return r && r.uniqueCount > 0 ? Math.round((r.knownCount / r.uniqueCount) * 100) : 0;
  });
  protected readonly bulkAdding = signal(false);

  constructor() {
    // Chọn sẵn bộ đầu tiên khi danh sách bộ tải xong
    effect(() => {
      const decks = this.myDecks();
      if (this.deckId() === null && decks.length > 0) {
        this.deckId.set(decks[0].id);
      }
    });
  }

  protected onInput(event: Event): void {
    this.text.set((event.target as HTMLTextAreaElement).value);
  }

  protected useSample(): void {
    this.text.set(SAMPLE_TEXT);
  }

  protected onDeckChange(event: Event): void {
    this.deckId.set(Number((event.target as HTMLSelectElement).value));
  }

  /** Ctrl / ⌘ + Enter trong ô nhập = Quét */
  protected onKeydown(event: KeyboardEvent): void {
    if (event.key === 'Enter' && (event.ctrlKey || event.metaKey)) {
      event.preventDefault();
      this.scan();
    }
  }

  protected scan(): void {
    if (!this.canScan()) {
      return;
    }
    this.scanning.set(true);
    this.scanError.set(null);
    this.scanApi.scan(this.text()).subscribe({
      next: (result) => {
        this.scanning.set(false);
        this.result.set(result);
        this.filter.set('ALL');
        this.rows.set(result.items.map((item) => ({
          item,
          example: item.example ? parseExample(item.example) : null,
          status: 'idle',
          addedTo: null,
          editing: false,
          term: item.term,
          partOfSpeech: 'NOUN',
          definition: '',
          error: null,
        })));
      },
      error: (error: unknown) => {
        this.scanning.set(false);
        this.scanError.set(errorMessage(error, 'Không quét được đoạn văn. Vui lòng thử lại.'));
      },
    });
  }

  protected clear(): void {
    this.text.set('');
    this.result.set(null);
    this.rows.set([]);
    this.scanError.set(null);
  }

  // ---------- Thêm / bỏ qua ----------

  /** LIBRARY: thêm từ hệ thống vào bộ đang chọn, 1 chạm. */
  protected addLibrary(row: Row): void {
    const deckId = this.deckId();
    if (deckId === null || row.item.wordId === null || row.status !== 'idle') {
      return;
    }
    this.patch(row, { status: 'saving' });
    this.deckApi.addWord(deckId, row.item.wordId).subscribe({
      next: () => this.patch(row, { status: 'added', addedTo: this.deckName() }),
      error: (error: unknown) => {
        this.patch(row, { status: 'idle' });
        this.toast.error(errorMessage(error));
      },
    });
  }

  /** Thêm lần lượt mọi từ LIBRARY chưa xử lý (tuần tự, để thứ tự trong bộ giống thứ tự trên màn hình). */
  protected addAllLibrary(): void {
    const deckId = this.deckId();
    const pending = this.rows().filter((r) => r.item.source === 'LIBRARY' && r.status === 'idle');
    if (deckId === null || pending.length === 0) {
      return;
    }
    const deckName = this.deckName();
    this.bulkAdding.set(true);
    pending.forEach((r) => this.patch(r, { status: 'saving' }));
    let done = 0;
    from(pending)
      // concatMap: gửi từng request, chờ xong mới gửi request sau; map(() => r) để biết dòng nào vừa xong
      .pipe(concatMap((r) => this.deckApi.addWord(deckId, r.item.wordId!).pipe(map(() => r))))
      .subscribe({
        next: (r) => {
          done++;
          this.patch(r, { status: 'added', addedTo: deckName });
        },
        error: (error: unknown) => {
          this.bulkAdding.set(false);
          this.rows.update((rows) => rows.map((x) => (x.status === 'saving' ? { ...x, status: 'idle' } : x)));
          this.toast.error(errorMessage(error));
        },
        complete: () => {
          this.bulkAdding.set(false);
          this.toast.success(`Đã thêm ${done} từ vào bộ "${deckName}"`);
        },
      });
  }

  protected openForm(row: Row): void {
    this.patch(row, { editing: true, error: null });
  }

  protected closeForm(row: Row): void {
    this.patch(row, { editing: false, error: null });
  }

  protected setTerm(row: Row, event: Event): void {
    this.patch(row, { term: (event.target as HTMLInputElement).value });
  }

  protected setDefinition(row: Row, event: Event): void {
    this.patch(row, { definition: (event.target as HTMLInputElement).value, error: null });
  }

  protected setPartOfSpeech(row: Row, event: Event): void {
    this.patch(row, { partOfSpeech: (event.target as HTMLSelectElement).value as PartOfSpeech });
  }

  /** NEW: tạo từ cá nhân có 1 nghĩa, câu ví dụ lấy từ đoạn văn (đã đánh dấu [ ] → luyện Điền từ được ngay). */
  protected saveNew(row: Row): void {
    const deckId = this.deckId();
    const term = row.term.trim();
    const definition = row.definition.trim();
    if (deckId === null) {
      return;
    }
    if (!term || !definition) {
      this.patch(row, { error: !term ? 'Nhập từ cần thêm' : 'Nhập nghĩa tiếng Việt' });
      return;
    }
    this.patch(row, { status: 'saving', error: null });
    this.wordApi.createWord({
      term,
      phonetic: null,
      senses: [{ id: null, partOfSpeech: row.partOfSpeech, definition, examples: row.item.example ? [row.item.example] : [] }],
      deckIds: [deckId],
    }).subscribe({
      next: () => this.patch(row, { status: 'added', addedTo: this.deckName(), editing: false }),
      error: (error: unknown) => {
        if (error instanceof ApiError && error.code === 'WORD_EXISTS') {
          // Từ đã có trong sổ (có thể vừa thêm ở tab khác): coi như xong
          this.patch(row, { status: 'added', addedTo: null, editing: false });
          this.toast.info(`"${term}" đã có trong sổ từ của bạn`);
          return;
        }
        this.patch(row, { status: 'idle', error: errorMessage(error) });
      },
    });
  }

  protected skip(row: Row): void {
    this.patch(row, { status: 'skipped', editing: false });
  }

  protected undoSkip(row: Row): void {
    this.patch(row, { status: 'idle' });
  }

  /** Các dạng khác với term (vd "running, ran" cho "run"), để hiện "gặp dưới dạng ..." */
  protected otherForms(row: Row): string {
    return row.item.forms.filter((f) => f !== row.item.term.toLowerCase()).join(', ');
  }

  private patch(row: Row, changes: Partial<Row>): void {
    this.rows.update((rows) => rows.map((r) => (r.item === row.item ? { ...r, ...changes } : r)));
  }
}
