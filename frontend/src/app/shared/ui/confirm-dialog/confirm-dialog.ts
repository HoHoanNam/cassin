import { ChangeDetectionStrategy, Component, input, output } from '@angular/core';

import { Modal } from '../modal/modal';

/**
 * Xác nhận thao tác KHÔNG hoàn tác được (xóa bộ, xóa thẻ, bỏ dở bài kiểm tra).
 * Nút nguy hiểm ghi rõ việc sẽ xảy ra ("Xóa bộ từ"), không ghi chung chung "Đồng ý".
 */
@Component({
  selector: 'app-confirm-dialog',
  imports: [Modal],
  templateUrl: './confirm-dialog.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ConfirmDialog {
  readonly open = input(false);
  readonly title = input.required<string>();
  readonly message = input('');
  readonly confirmLabel = input.required<string>();
  readonly cancelLabel = input('Hủy');
  readonly danger = input(true);
  /** Đang xử lý: khóa nút, không cho đóng giữa chừng. */
  readonly busy = input(false);

  readonly confirmed = output<void>();
  readonly cancelled = output<void>();

  protected cancel(): void {
    if (!this.busy()) {
      this.cancelled.emit();
    }
  }
}
