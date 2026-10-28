import { ChangeDetectionStrategy, Component, ElementRef, computed, inject, input, signal } from '@angular/core';

import { AuthService } from '../../core/auth/auth.service';
import { AuthStore } from '../../core/auth/auth.store';
import { APP_ENV } from '../../core/config/app-env';
import { MockDb } from '../../core/mock/mock-db';
import { ToastService } from '../../core/ui/toast.service';
import { initial } from '../../core/util/text';
import { Icon } from '../../shared/ui/icon/icon';

/** Avatar + menu tài khoản (đăng xuất...). variant "full": hiện cả tên và email (sidebar). */
@Component({
  selector: 'app-user-menu',
  imports: [Icon],
  templateUrl: './user-menu.html',
  styleUrl: './user-menu.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: {
    '(document:click)': 'onDocumentClick($event)',
    '(document:keydown.escape)': 'open.set(false)',
  },
})
export class UserMenu {
  readonly variant = input<'full' | 'avatar'>('full');
  /** Menu mở lên trên (sidebar ở đáy) hay xuống dưới (thanh trên cùng) */
  readonly placement = input<'up' | 'down'>('up');

  private readonly auth = inject(AuthService);
  private readonly host = inject(ElementRef<HTMLElement>);
  private readonly mockDb = inject(MockDb);
  private readonly toast = inject(ToastService);

  protected readonly user = inject(AuthStore).user;
  protected readonly avatar = computed(() => initial(this.user()?.displayName));
  protected readonly open = signal(false);
  protected readonly isMock = APP_ENV.useMockApi;

  protected toggle(): void {
    this.open.update((v) => !v);
  }

  protected logout(): void {
    this.open.set(false);
    this.auth.logout();
  }

  /** Chỉ có ở chế độ mock: xóa mọi thay đổi, quay về dữ liệu mẫu ban đầu. */
  protected resetMockData(): void {
    this.mockDb.reset();
    this.open.set(false);
    this.toast.info('Đã đặt lại dữ liệu mẫu. Tải lại trang để thấy thay đổi.');
  }

  /** Bấm ra ngoài menu thì đóng. */
  protected onDocumentClick(event: MouseEvent): void {
    if (this.open() && !this.host.nativeElement.contains(event.target as Node)) {
      this.open.set(false);
    }
  }
}
