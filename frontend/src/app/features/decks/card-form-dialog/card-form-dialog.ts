import { ChangeDetectionStrategy, Component, effect, inject, input, output, signal } from '@angular/core';
import { FormControl, NonNullableFormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';

import { DeckApi } from '../../../core/api/deck.api';
import { ApiError, errorMessage } from '../../../core/models/common.models';
import { CARD_LIMITS, Card, CardRequest, PartOfSpeech } from '../../../core/models/deck.models';
import { PART_OF_SPEECH_OPTIONS } from '../../../core/util/part-of-speech';
import { blankToNull } from '../../../core/util/text';
import { Icon } from '../../../shared/ui/icon/icon';
import { Modal } from '../../../shared/ui/modal/modal';

/**
 * Hộp thoại thêm / sửa thẻ. card = null: thêm mới vào deckId.
 * Khi thêm mới có nút "Lưu và thêm tiếp": lưu xong giữ hộp thoại mở, xóa trắng form,
 * để nhập liền nhiều thẻ không phải mở lại hộp thoại.
 */
@Component({
  selector: 'app-card-form-dialog',
  imports: [ReactiveFormsModule, Modal, Icon],
  templateUrl: './card-form-dialog.html',
  styleUrl: './card-form-dialog.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class CardFormDialog {
  readonly open = input(false);
  readonly deckId = input.required<number>();
  readonly card = input<Card | null>(null);
  readonly saved = output<{ card: Card; keepOpen: boolean }>();
  readonly closed = output<void>();

  private readonly api = inject(DeckApi);
  private readonly fb = inject(NonNullableFormBuilder);

  protected readonly limits = CARD_LIMITS;
  protected readonly posOptions = PART_OF_SPEECH_OPTIONS;
  protected readonly saving = signal(false);
  protected readonly serverError = signal<string | null>(null);

  protected readonly form = this.fb.group({
    word: ['', [Validators.required, Validators.maxLength(CARD_LIMITS.word)]],
    partOfSpeech: this.fb.control<PartOfSpeech>('NOUN', Validators.required),
    phonetic: ['', Validators.maxLength(CARD_LIMITS.phonetic)],
    definition: ['', [Validators.required, Validators.maxLength(CARD_LIMITS.definition)]],
    // FormArray: số câu ví dụ thay đổi được (0..5)
    examples: this.fb.array<FormControl<string>>([]),
  });

  constructor() {
    effect(() => {
      if (this.open()) {
        this.fill(this.card());
      }
    });
  }

  protected get examples() {
    return this.form.controls.examples;
  }

  protected addExample(): void {
    if (this.examples.length < CARD_LIMITS.maxExamples) {
      this.examples.push(this.exampleControl(''));
    }
  }

  protected removeExample(index: number): void {
    this.examples.removeAt(index);
  }

  protected close(): void {
    if (!this.saving()) {
      this.closed.emit();
    }
  }

  protected submit(keepOpen: boolean): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    const v = this.form.getRawValue();
    const request: CardRequest = {
      word: v.word.trim(),
      partOfSpeech: v.partOfSpeech,
      phonetic: blankToNull(v.phonetic),
      definition: v.definition.trim(),
      examples: v.examples.map((e) => e.trim()).filter((e) => e.length > 0),
    };
    const card = this.card();
    this.saving.set(true);
    this.serverError.set(null);
    (card ? this.api.updateCard(card.id, request) : this.api.createCard(this.deckId(), request)).subscribe({
      next: (result) => {
        this.saving.set(false);
        this.saved.emit({ card: result, keepOpen });
        if (keepOpen) {
          // Giữ lại loại từ vừa chọn: thường nhập liền nhiều từ cùng loại.
          this.fill(null, v.partOfSpeech);
        }
      },
      error: (error: unknown) => {
        this.saving.set(false);
        if (error instanceof ApiError && error.code === 'DATA_CONFLICT') {
          this.serverError.set('Bộ này đã có thẻ cùng từ và cùng nghĩa. Từ nhiều nghĩa thì tách mỗi nghĩa một thẻ.');
        } else if (error instanceof ApiError && Object.keys(error.fieldErrors).length > 0) {
          for (const [field, message] of Object.entries(error.fieldErrors)) {
            this.form.get(field)?.setErrors({ server: message });
          }
        } else {
          this.serverError.set(errorMessage(error));
        }
      },
    });
  }

  private fill(card: Card | null, partOfSpeech: PartOfSpeech = 'NOUN'): void {
    this.examples.clear();
    (card?.examples.length ? card.examples : ['']).forEach((e) => this.examples.push(this.exampleControl(e)));
    this.form.reset({
      word: card?.word ?? '',
      partOfSpeech: card?.partOfSpeech ?? partOfSpeech,
      phonetic: card?.phonetic ?? '',
      definition: card?.definition ?? '',
      examples: card?.examples.length ? card.examples : [''],
    });
    this.serverError.set(null);
  }

  private exampleControl(value: string): FormControl<string> {
    return this.fb.control(value, Validators.maxLength(CARD_LIMITS.example));
  }
}
