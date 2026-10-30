import { ChangeDetectionStrategy, Component, effect, inject, input, output, signal } from '@angular/core';
import { NonNullableFormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';

import { AdminApi } from '../../../core/api/admin.api';
import { DeckApi } from '../../../core/api/deck.api';
import { ApiError, errorMessage } from '../../../core/models/common.models';
import { DECK_LIMITS, Deck } from '../../../core/models/deck.models';
import { blankToNull } from '../../../core/util/text';
import { Modal } from '../../../shared/ui/modal/modal';

/**
 * Hộp thoại tạo / sửa bộ từ. deck = null: tạo mới; có deck: sửa.
 * scope = 'system': admin quản lý bộ HỆ THỐNG → gọi /api/admin/decks thay vì /api/decks.
 */
@Component({
  selector: 'app-deck-form-dialog',
  imports: [ReactiveFormsModule, Modal],
  templateUrl: './deck-form-dialog.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class DeckFormDialog {
  readonly open = input(false);
  readonly deck = input<Deck | null>(null);
  readonly scope = input<'mine' | 'system'>('mine');
  readonly saved = output<Deck>();
  readonly closed = output<void>();

  private readonly api = inject(DeckApi);
  private readonly adminApi = inject(AdminApi);

  protected readonly limits = DECK_LIMITS;
  protected readonly saving = signal(false);
  protected readonly serverError = signal<string | null>(null);
  protected readonly form = inject(NonNullableFormBuilder).group({
    deckName: ['', [Validators.required, Validators.maxLength(DECK_LIMITS.deckName)]],
    description: ['', Validators.maxLength(DECK_LIMITS.description)],
  });

  constructor() {
    // Mỗi lần mở hộp thoại: đổ dữ liệu của bộ đang sửa (hoặc để trống khi tạo mới).
    effect(() => {
      if (this.open()) {
        const deck = this.deck();
        this.form.reset({ deckName: deck?.deckName ?? '', description: deck?.description ?? '' });
        this.serverError.set(null);
      }
    });
  }

  protected close(): void {
    if (!this.saving()) {
      this.closed.emit();
    }
  }

  protected submit(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    const value = this.form.getRawValue();
    const request = { deckName: value.deckName.trim(), description: blankToNull(value.description) };
    const deck = this.deck();
    this.saving.set(true);
    this.serverError.set(null);
    const api = this.scope() === 'system' ? this.adminApi : this.api;
    (deck ? api.updateDeck(deck.id, request) : api.createDeck(request)).subscribe({
      next: (result) => {
        this.saving.set(false);
        this.saved.emit(result);
      },
      error: (error: unknown) => {
        this.saving.set(false);
        if (error instanceof ApiError && error.fieldErrors['deckName']) {
          this.form.controls.deckName.setErrors({ server: error.fieldErrors['deckName'] });
        } else {
          this.serverError.set(errorMessage(error));
        }
      },
    });
  }
}
