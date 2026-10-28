import { Injectable, signal } from '@angular/core';

export type ToastKind = 'success' | 'error' | 'info';

export interface Toast {
  id: number;
  kind: ToastKind;
  message: string;
  /** Nút hành động tùy chọn, ví dụ "Thử lại" */
  action?: { label: string; run: () => void };
}

/**
 * Thông báo nhỏ ở góc màn hình, tự ẩn sau 4 giây (lỗi: 7 giây để kịp đọc).
 * Câu thông báo dùng cùng động từ với nút vừa bấm: bấm "Lưu thẻ" → "Đã lưu thẻ ...".
 */
@Injectable({ providedIn: 'root' })
export class ToastService {
  private nextId = 1;
  readonly toasts = signal<Toast[]>([]);

  success(message: string): void {
    this.show({ kind: 'success', message });
  }

  error(message: string, action?: Toast['action']): void {
    this.show({ kind: 'error', message, action });
  }

  info(message: string): void {
    this.show({ kind: 'info', message });
  }

  dismiss(id: number): void {
    this.toasts.update((list) => list.filter((t) => t.id !== id));
  }

  private show(toast: Omit<Toast, 'id'>): void {
    const id = this.nextId++;
    // Giữ tối đa 3 thông báo: thông báo cũ nhất bị đẩy ra.
    this.toasts.update((list) => [...list.slice(-2), { ...toast, id }]);
    setTimeout(() => this.dismiss(id), toast.kind === 'error' ? 7000 : 4000);
  }
}
