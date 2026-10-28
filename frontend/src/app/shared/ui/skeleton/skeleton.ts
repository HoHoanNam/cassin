import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';

/**
 * Khung xương khi đang tải: đúng HÌNH DẠNG nội dung sắp hiện,
 * nên trang không bị xô lệch khi dữ liệu về (tốt hơn một vòng xoay ở giữa trang).
 */
@Component({
  selector: 'app-skeleton',
  templateUrl: './skeleton.html',
  styleUrl: './skeleton.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: { 'aria-busy': 'true', 'aria-label': 'Đang tải' },
})
export class Skeleton {
  /** list: các dòng danh sách; text: vài dòng chữ; card: khối lớn */
  readonly variant = input<'list' | 'text' | 'card'>('list');
  readonly rows = input(3);

  protected readonly items = computed(() => Array.from({ length: this.rows() }, (_, i) => i));
}
