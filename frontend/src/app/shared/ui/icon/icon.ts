import { ChangeDetectionStrategy, Component, input } from '@angular/core';

export type IconName =
  | 'today' | 'cards' | 'decks' | 'grammar' | 'history'
  | 'plus' | 'search' | 'close' | 'chevron-down' | 'chevron-left' | 'chevron-right'
  | 'check' | 'alert' | 'info' | 'edit' | 'trash' | 'logout' | 'refresh' | 'arrow-left' | 'flame' | 'reset'
  | 'shield' | 'user' | 'eye' | 'users' | 'chart';

/**
 * Icon nét mảnh (stroke) vẽ bằng SVG, dùng chung cả app.
 * Luôn có aria-hidden: icon chỉ để trang trí, chữ bên cạnh mới mang nghĩa.
 * Nút CHỈ có icon (không chữ) thì phải tự ghi aria-label trên nút.
 */
@Component({
  selector: 'app-icon',
  templateUrl: './icon.html',
  styleUrl: './icon.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: { 'aria-hidden': 'true' },
})
export class Icon {
  readonly name = input.required<IconName>();
  readonly size = input(20);
  readonly strokeWidth = input(1.8);
}
