import {
  ChangeDetectionStrategy,
  Component,
  effect,
  inject,
  input,
  output,
  signal,
} from '@angular/core';
import {
  AbstractControl,
  FormArray,
  FormControl,
  FormGroup,
  NonNullableFormBuilder,
  ReactiveFormsModule,
  ValidationErrors,
  Validators,
} from '@angular/forms';

import { AdminApi } from '../../../core/api/admin.api';
import { WordApi } from '../../../core/api/word.api';
import { ApiError, errorMessage } from '../../../core/models/common.models';
import {
  PartOfSpeech,
  Sense,
  WORD_LIMITS,
  Word,
  WordRequest,
} from '../../../core/models/deck.models';
import { exampleMarkupError } from '../../../core/util/example-markup';
import { PART_OF_SPEECH_OPTIONS } from '../../../core/util/part-of-speech';
import { blankToNull } from '../../../core/util/text';
import { Icon } from '../../../shared/ui/icon/icon';
import { Modal } from '../../../shared/ui/modal/modal';

/** Một nghĩa trong form: id = null là nghĩa mới. */
type SenseForm = FormGroup<{
  id: FormControl<number | null>;
  partOfSpeech: FormControl<PartOfSpeech>;
  definition: FormControl<string>;
  examples: FormArray<FormControl<string>>;
}>;

/** Câu ví dụ đánh dấu sai ("[improved" thiếu ngoặc, hai chỗ [ ]...) → lỗi { markup: 'lý do' }. */
function markupValidator(control: AbstractControl<string>): ValidationErrors | null {
  const message = exampleMarkupError((control.value ?? '').trim());
  return message ? { markup: message } : null;
}

/**
 * [V10] Hộp thoại thêm / sửa MỘT TỪ kèm nhiều nghĩa (thay cho hộp thoại thẻ cũ).
 *
 *   wordId = null : thêm từ mới. deckId có giá trị thì đặt luôn từ vào bộ đó.
 *   wordId có     : tải từ về rồi sửa. Nghĩa cũ giữ id → giữ tiến độ ôn; nghĩa bị xóa → mất tiến độ.
 *   scope='system': từ HỆ THỐNG (admin) → gọi /api/admin/... thay vì /api/words
 *
 * Câu ví dụ có thể đánh dấu từ cần điền bằng [ ] (dùng cho bài luyện "Điền từ").
 */
@Component({
  selector: 'app-word-form-dialog',
  imports: [ReactiveFormsModule, Modal, Icon],
  templateUrl: './word-form-dialog.html',
  styleUrl: './word-form-dialog.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class WordFormDialog {
  readonly open = input(false);
  readonly wordId = input<number | null>(null);
  readonly deckId = input<number | null>(null);
  readonly scope = input<'mine' | 'system'>('mine');
  readonly saved = output<{ word: Word; created: boolean; keepOpen: boolean }>();
  readonly closed = output<void>();

  private readonly wordApi = inject(WordApi);
  private readonly adminApi = inject(AdminApi);
  private readonly fb = inject(NonNullableFormBuilder);

  protected readonly limits = WORD_LIMITS;
  protected readonly posOptions = PART_OF_SPEECH_OPTIONS;
  protected readonly loading = signal(false);
  protected readonly saving = signal(false);
  protected readonly serverError = signal<string | null>(null);

  protected readonly form = this.fb.group({
    term: ['', [Validators.required, Validators.maxLength(WORD_LIMITS.term)]],
    phonetic: ['', Validators.maxLength(WORD_LIMITS.phonetic)],
    senses: this.fb.array<SenseForm>([]),
  });

  constructor() {
    effect(() => {
      if (this.open()) {
        this.start(this.wordId());
      }
    });
  }

  protected get senses(): FormArray<SenseForm> {
    return this.form.controls.senses;
  }

  protected isEdit(): boolean {
    return this.wordId() !== null;
  }

  protected addSense(): void {
    if (this.senses.length < WORD_LIMITS.maxSenses) {
      // Nghĩa mới thường cùng loại với nghĩa ngay trên (vd 2 nghĩa danh từ)
      const last = this.senses.at(this.senses.length - 1);
      this.senses.push(this.senseGroup(null, last?.controls.partOfSpeech.value ?? 'NOUN'));
    }
  }

  protected removeSense(index: number): void {
    if (this.senses.length > 1) {
      this.senses.removeAt(index);
    }
  }

  protected addExample(sense: SenseForm): void {
    if (sense.controls.examples.length < WORD_LIMITS.maxExamples) {
      sense.controls.examples.push(this.exampleControl(''));
    }
  }

  protected removeExample(sense: SenseForm, index: number): void {
    sense.controls.examples.removeAt(index);
  }

  protected close(): void {
    if (!this.saving()) {
      this.closed.emit();
    }
  }

  protected submit(keepOpen: boolean): void {
    if (this.form.invalid || this.loading()) {
      this.form.markAllAsTouched();
      return;
    }
    const v = this.form.getRawValue();
    const editingId = this.wordId();
    const deckId = this.deckId();
    const request: WordRequest = {
      term: v.term.trim(),
      phonetic: blankToNull(v.phonetic),
      senses: v.senses.map((s) => ({
        id: s.id,
        partOfSpeech: s.partOfSpeech,
        definition: s.definition.trim(),
        examples: s.examples.map((e) => e.trim()).filter((e) => e.length > 0),
      })),
      // Thêm mới: đặt vào bộ đang mở (nếu có). Sửa: null = giữ nguyên các bộ đang chứa từ.
      deckIds: editingId !== null ? null : deckId !== null ? [deckId] : [],
    };

    let call;
    if (this.scope() === 'system') {
      if (editingId === null && deckId === null) {
        this.serverError.set('Từ hệ thống phải được tạo bên trong một bộ hệ thống.');
        return;
      }
      call =
        editingId !== null
          ? this.adminApi.updateWord(editingId, request)
          : this.adminApi.createWord(deckId!, request);
    } else {
      call =
        editingId !== null
          ? this.wordApi.updateWord(editingId, request)
          : this.wordApi.createWord(request);
    }

    this.saving.set(true);
    this.serverError.set(null);
    call.subscribe({
      next: (word) => {
        this.saving.set(false);
        this.saved.emit({ word, created: editingId === null, keepOpen });
        if (keepOpen) {
          // Giữ lại loại từ của nghĩa đầu: thường nhập liền nhiều từ cùng loại.
          this.fill(null, v.senses[0]?.partOfSpeech ?? 'NOUN');
        }
      },
      error: (error: unknown) => {
        this.saving.set(false);
        this.showError(error);
      },
    });
  }

  private showError(error: unknown): void {
    if (error instanceof ApiError && Object.keys(error.fieldErrors).length > 0) {
      // "senses[1].definition" → "senses.1.definition" (đường dẫn của Reactive Forms)
      let placed = false;
      for (const [field, message] of Object.entries(error.fieldErrors)) {
        const control = this.form.get(field.replace(/\[(\d+)\]/g, '.$1'));
        if (control) {
          control.setErrors({ server: message });
          control.markAsTouched();
          placed = true;
        }
      }
      if (!placed) {
        this.serverError.set(error.message);
      }
    } else {
      // WORD_EXISTS (trùng từ), INVALID_WORD (2 nghĩa trùng, đánh dấu sai...), WORD_READ_ONLY...
      this.serverError.set(errorMessage(error));
    }
  }

  /** Mở hộp thoại: thêm mới → form trống; sửa → tải từ về rồi mới điền. */
  private start(wordId: number | null): void {
    this.serverError.set(null);
    if (wordId === null) {
      this.fill(null);
      return;
    }
    this.loading.set(true);
    this.form.disable();
    this.wordApi.getWord(wordId).subscribe({
      next: (word) => {
        this.loading.set(false);
        this.form.enable();
        this.fill(word);
      },
      error: (error: unknown) => {
        this.loading.set(false);
        this.serverError.set(errorMessage(error, 'Không tải được từ này. Vui lòng thử lại.'));
      },
    });
  }

  private fill(word: Word | null, partOfSpeech: PartOfSpeech = 'NOUN'): void {
    this.senses.clear();
    const senses = word?.senses.length ? word.senses : [null];
    senses.forEach((s) => this.senses.push(this.senseGroup(s, partOfSpeech)));
    this.form.controls.term.reset(word?.term ?? '');
    this.form.controls.phonetic.reset(word?.phonetic ?? '');
    this.serverError.set(null);
  }

  private senseGroup(sense: Sense | null, partOfSpeech: PartOfSpeech): SenseForm {
    const examples = sense?.examples.length ? sense.examples : [''];
    return this.fb.group({
      id: new FormControl<number | null>(sense?.id ?? null),
      partOfSpeech: this.fb.control<PartOfSpeech>(
        sense?.partOfSpeech ?? partOfSpeech,
        Validators.required,
      ),
      definition: this.fb.control(sense?.definition ?? '', [
        Validators.required,
        Validators.maxLength(WORD_LIMITS.definition),
      ]),
      examples: this.fb.array(examples.map((e) => this.exampleControl(e))),
    });
  }

  private exampleControl(value: string): FormControl<string> {
    return this.fb.control(value, [Validators.maxLength(WORD_LIMITS.example), markupValidator]);
  }
}
