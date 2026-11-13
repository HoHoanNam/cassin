import { ChangeDetectionStrategy, Component, computed, effect, inject, input, output, signal } from '@angular/core';

import { DeckApi } from '../../../core/api/deck.api';
import { errorMessage } from '../../../core/models/common.models';
import { IMPORT_MAX_BYTES, IMPORT_MAX_ROWS, IMPORT_TEMPLATE, ImportResult } from '../../../core/models/import.models';
import { Icon } from '../../../shared/ui/icon/icon';
import { Modal } from '../../../shared/ui/modal/modal';

/** Hộp thoại đi qua 3 bước. */
type Step = 'pick' | 'preview' | 'done';

/** Tên cột trong file → tên dễ hiểu khi báo lỗi */
const FIELD_LABELS: Record<string, string> = {
  term: 'Từ',
  phonetic: 'Phiên âm',
  part_of_speech: 'Loại từ',
  definition: 'Nghĩa',
};

/**
 * Nhập từ vào bộ bằng file CSV. Hai lần gọi cùng một endpoint:
 *
 *   ① chọn file  → POST /api/decks/{id}/import?dryRun=true   : kiểm tra + đếm, KHÔNG lưu
 *   ② xem trước  → "Sẽ nhập 42 từ" hoặc bảng lỗi theo dòng
 *   ③ xác nhận   → POST ...?dryRun=false                     : lưu thật (tất cả hoặc không gì cả)
 *
 * Kiểm tra trước trên trình duyệt: đuôi .csv và ≤ 1MB (server vẫn kiểm tra lại).
 */
@Component({
  selector: 'app-import-dialog',
  imports: [Modal, Icon],
  templateUrl: './import-dialog.html',
  styleUrl: './import-dialog.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ImportDialog {
  readonly open = input(false);
  readonly deckId = input.required<number>();
  readonly deckName = input('');
  readonly imported = output<ImportResult>();
  readonly closed = output<void>();

  private readonly api = inject(DeckApi);

  protected readonly maxRows = IMPORT_MAX_ROWS;
  protected readonly step = signal<Step>('pick');
  protected readonly file = signal<File | null>(null);
  protected readonly preview = signal<ImportResult | null>(null);
  protected readonly busy = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly dragging = signal(false);

  protected readonly hasErrors = computed(() => (this.preview()?.errors.length ?? 0) > 0);
  protected readonly nothingNew = computed(() => {
    const p = this.preview();
    return p !== null && p.errors.length === 0 && p.words === 0;
  });

  /** Link tải file mẫu: tạo từ chuỗi trong bộ nhớ, không cần file trên server. */
  protected readonly templateUrl = URL.createObjectURL(new Blob([IMPORT_TEMPLATE], { type: 'text/csv;charset=utf-8' }));

  constructor() {
    // Mỗi lần mở: về bước đầu
    effect(() => {
      if (this.open()) {
        this.reset();
      }
    });
  }

  protected close(): void {
    if (!this.busy()) {
      this.closed.emit();
    }
  }

  protected onFileInput(event: Event): void {
    const input = event.target as HTMLInputElement;
    const file = input.files?.[0];
    input.value = '';   // chọn lại đúng file cũ (sau khi sửa) vẫn kích hoạt change
    if (file) {
      this.check(file);
    }
  }

  protected onDragOver(event: DragEvent): void {
    event.preventDefault();
    this.dragging.set(true);
  }

  protected onDrop(event: DragEvent): void {
    event.preventDefault();
    this.dragging.set(false);
    const file = event.dataTransfer?.files?.[0];
    if (file) {
      this.check(file);
    }
  }

  /** ① Kiểm tra nhanh trên trình duyệt rồi gọi dryRun. */
  private check(file: File): void {
    this.error.set(null);
    if (!file.name.toLowerCase().endsWith('.csv')) {
      this.error.set('Chỉ nhận file .csv. Trong Excel: File → Save As → "CSV UTF-8".');
      return;
    }
    if (file.size > IMPORT_MAX_BYTES) {
      this.error.set('File tối đa 1MB. Hãy chia thành nhiều file nhỏ hơn.');
      return;
    }
    this.file.set(file);
    this.busy.set(true);
    this.api.importWords(this.deckId(), file, true).subscribe({
      next: (result) => {
        this.busy.set(false);
        this.preview.set(result);
        this.step.set('preview');
      },
      error: (error: unknown) => {
        this.busy.set(false);
        this.file.set(null);
        this.error.set(errorMessage(error, 'Không đọc được file. Vui lòng thử lại.'));
      },
    });
  }

  /** ③ Nhập thật. Server kiểm tra lại từ đầu (file có thể đã khác lúc xem trước). */
  protected confirm(): void {
    const file = this.file();
    if (!file || this.busy()) {
      return;
    }
    this.busy.set(true);
    this.error.set(null);
    this.api.importWords(this.deckId(), file, false).subscribe({
      next: (result) => {
        this.busy.set(false);
        if (result.imported) {
          this.preview.set(result);
          this.step.set('done');
          this.imported.emit(result);
        } else {
          // Hiếm: dữ liệu đổi giữa lúc xem trước và lúc nhập (vd vừa thêm tay một từ trùng)
          this.preview.set(result);
          this.step.set('preview');
        }
      },
      error: (error: unknown) => {
        this.busy.set(false);
        this.error.set(errorMessage(error, 'Không nhập được. Vui lòng thử lại.'));
      },
    });
  }

  protected chooseAnother(): void {
    this.reset();
  }

  protected fieldLabel(field: string | null): string {
    if (field === null) {
      return 'Cả từ';
    }
    const example = /^example_(\d)$/.exec(field);
    return example ? `Ví dụ ${example[1]}` : (FIELD_LABELS[field] ?? field);
  }

  protected sizeLabel(bytes: number): string {
    return bytes < 1024 ? `${bytes} B` : `${(bytes / 1024).toFixed(1)} KB`;
  }

  private reset(): void {
    this.step.set('pick');
    this.file.set(null);
    this.preview.set(null);
    this.error.set(null);
    this.busy.set(false);
  }
}
