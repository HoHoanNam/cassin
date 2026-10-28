import { ChangeDetectionStrategy, Component, input } from '@angular/core';

/**
 * Logo Cassin = biểu tượng + chữ.
 *
 * Biểu tượng: hai tấm thẻ học chồng lên nhau. Thẻ trước có đường lề đỏ như vở ô li
 * và một dấu tích bút xanh, nghĩa là "đã nhớ" (bấm Nhớ → thẻ lên hộp sau).
 * Chữ "Cassin" viết tay (Patrick Hand), cùng kiểu chữ tiêu đề của app.
 *
 * variant "light": dùng trên nền tối (chân trang landing) → chữ trắng.
 * Logo vẽ bằng SVG ngay trong HTML: nét sắc ở mọi kích thước, đổi màu bằng CSS, không tải thêm file.
 */
@Component({
  selector: 'app-logo',
  templateUrl: './logo.html',
  styleUrl: './logo.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class Logo {
  /** Chiều cao biểu tượng (px). Chữ tự co theo. */
  readonly size = input(32);
  readonly showWordmark = input(true);
  readonly variant = input<'default' | 'light'>('default');
}
