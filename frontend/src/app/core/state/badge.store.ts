import { Injectable, computed, inject, signal } from '@angular/core';

import { BadgeApi } from '../api/badge.api';
import { EarnedBadge } from '../models/badge.models';

/**
 * [THÊM] Hàng đợi huy hiệu vừa mở khóa, để hiện popup chúc mừng.
 *
 * Vì sao hỏi "unseen" thay vì để mỗi API (nộp bài, ôn thẻ...) trả kèm huy hiệu mới?
 *   - Không phải sửa DTO / component của những module đang chạy ổn: Badge cắm thêm vào, không đụng chạm.
 *   - Huy hiệu được cấp ở listener SAU khi commit, có thể chậm hơn response vài mili giây.
 *   - Đăng nhập trên máy khác vẫn thấy popup những huy hiệu chưa xem.
 *
 * AppShell gọi check() mỗi lần chuyển trang trong khu vực đăng nhập (sau khi ôn / làm bài xong
 * người học luôn quay về một trang trong khung) → popup hiện đúng lúc, không cần polling.
 */
@Injectable({ providedIn: 'root' })
export class BadgeStore {
  private readonly api = inject(BadgeApi);

  /** Huy hiệu đang chờ hiện popup, cũ nhất trước. */
  private readonly queue = signal<EarnedBadge[]>([]);
  /** Vị trí huy hiệu đang hiện trong hàng đợi. */
  private readonly index = signal(0);
  private checking = false;
  /**
   * Cặp "mã:cấp" đã đóng popup trong phiên này. Đóng popup rồi chuyển trang ngay thì check() có thể chạy
   * TRƯỚC khi POST /seen kịp xong → server vẫn trả lại những mã đó. Lọc bỏ ở đây để popup không hiện lại.
   */
  private readonly dismissed = new Set<string>();

  readonly current = computed(() => this.queue().at(this.index()) ?? null);
  readonly total = computed(() => this.queue().length);
  readonly position = computed(() => this.index() + 1);
  readonly isLast = computed(() => this.index() >= this.queue().length - 1);

  check(): void {
    // Popup đang mở hoặc đang hỏi dở: bỏ qua, lần chuyển trang sau sẽ hỏi lại.
    if (this.checking || this.queue().length > 0) {
      return;
    }
    this.checking = true;
    this.api.unseen().subscribe({
      next: (badges) => {
        this.index.set(0);
        this.queue.set(badges.filter((b) => !this.dismissed.has(`${b.code}:${b.level}`)));
        this.checking = false;
      },
      // Huy hiệu là phần phụ: lỗi thì im lặng, không làm phiền người học.
      error: () => (this.checking = false),
    });
  }

  next(): void {
    if (this.isLast()) {
      this.finish();
    } else {
      this.index.update((i) => i + 1);
    }
  }

  /** Đóng popup: đánh dấu đã xem TẤT CẢ huy hiệu trong hàng đợi (kể cả những cái bỏ qua bằng Esc). */
  finish(): void {
    const items = this.queue().map((b) => ({ code: b.code, level: b.level }));
    items.forEach((i) => this.dismissed.add(`${i.code}:${i.level}`));
    this.queue.set([]);
    this.index.set(0);
    if (items.length > 0) {
      this.api.markSeen(items).subscribe({ error: () => undefined });
    }
  }
}
