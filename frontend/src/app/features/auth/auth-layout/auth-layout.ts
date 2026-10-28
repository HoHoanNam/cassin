import { ChangeDetectionStrategy, Component, input } from '@angular/core';
import { RouterLink } from '@angular/router';

import { Logo } from '../../../shared/ui/logo/logo';

/**
 * Bố cục chung của trang đăng nhập / đăng ký:
 * cột form trên nền giấy trơn + cột minh họa trên nền vở ô li (ẩn trên điện thoại).
 */
@Component({
  selector: 'app-auth-layout',
  imports: [RouterLink, Logo],
  templateUrl: './auth-layout.html',
  styleUrl: './auth-layout.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class AuthLayout {
  readonly word = input.required<string>();
  readonly phonetic = input.required<string>();
  readonly caption = input.required<string>();
}
