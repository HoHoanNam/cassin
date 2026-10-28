import { ChangeDetectionStrategy, Component, input, output } from '@angular/core';

import { Icon } from '../icon/icon';

/**
 * Lỗi khi tải MỘT khối dữ liệu. Chỉ khối đó báo lỗi, phần còn lại của trang vẫn dùng được.
 */
@Component({
  selector: 'app-error-state',
  imports: [Icon],
  templateUrl: './error-state.html',
  styleUrl: './error-state.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ErrorState {
  readonly title = input('Không tải được dữ liệu');
  readonly message = input('Kiểm tra kết nối mạng rồi thử lại.');
  readonly retry = output<void>();
}
