import {
  ChangeDetectionStrategy,
  Component,
  ElementRef,
  effect,
  input,
  output,
  viewChild,
} from '@angular/core';

import { Icon } from '../icon/icon';

let nextId = 0;

/**
 * Hộp thoại dùng thẻ <dialog> có sẵn của trình duyệt (showModal):
 * tự khóa nền phía sau, tự giữ focus bên trong, phím Esc đóng được, không cần thư viện.
 *
 * Cha điều khiển bằng [open]; khi người dùng muốn đóng (Esc, bấm nền, nút X)
 * component phát (closed) để CHA quyết định. Nhờ vậy cha có thể chặn đóng khi đang lưu dở.
 */
@Component({
  selector: 'app-modal',
  imports: [Icon],
  templateUrl: './modal.html',
  styleUrl: './modal.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class Modal {
  readonly open = input(false);
  readonly title = input.required<string>();
  readonly size = input<'sm' | 'md'>('md');
  readonly closed = output<void>();

  protected readonly titleId = `modal-title-${nextId++}`;
  private readonly dialog = viewChild.required<ElementRef<HTMLDialogElement>>('dialog');

  constructor() {
    effect(() => {
      const el = this.dialog().nativeElement;
      if (this.open() && !el.open) {
        el.showModal();
      } else if (!this.open() && el.open) {
        el.close();
      }
    });
  }

  /** Esc: chặn trình duyệt tự đóng, báo cho cha. */
  protected onCancel(event: Event): void {
    event.preventDefault();
    this.closed.emit();
  }

  /** Bấm ra vùng nền tối (chính thẻ dialog, không phải nội dung bên trong) → muốn đóng. */
  protected onDialogClick(event: MouseEvent): void {
    if (event.target === this.dialog().nativeElement) {
      this.closed.emit();
    }
  }
}
