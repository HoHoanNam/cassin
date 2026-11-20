import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { rxResource } from '@angular/core/rxjs-interop';

import { SessionApi } from '../../../core/api/session.api';
import { ApiError, errorMessage } from '../../../core/models/common.models';
import { MOBILE_OS, Session } from '../../../core/models/session.models';
import { ToastService } from '../../../core/ui/toast.service';
import { relativeDay, timeAgo } from '../../../core/util/dates';
import { ConfirmDialog } from '../../../shared/ui/confirm-dialog/confirm-dialog';
import { ErrorState } from '../../../shared/ui/error-state/error-state';
import { Icon } from '../../../shared/ui/icon/icon';
import { Skeleton } from '../../../shared/ui/skeleton/skeleton';

/** Đang hỏi xác nhận: đăng xuất một thiết bị, hoặc mọi thiết bị khác. */
type Pending = { kind: 'one'; session: Session } | { kind: 'others' };

/**
 * [THÊM] "Thiết bị đang đăng nhập" trong trang Tài khoản (GET / DELETE /api/auth/sessions).
 *
 *   ● Chrome · Windows   Đăng nhập 18/11 · Đang dùng   (thiết bị này)
 *   ● Safari · iOS       Đăng nhập 10/11 · 2 giờ trước     [Đăng xuất]
 *
 * Thiết bị đang dùng không có nút đăng xuất ở đây: muốn thoát thì dùng "Đăng xuất" ở menu tài khoản.
 * Đăng xuất thiết bị khác chặn được làm mới token ngay; thiết bị đó còn dùng được tối đa 15 phút (TTL access token).
 */
@Component({
  selector: 'app-session-list',
  imports: [ConfirmDialog, ErrorState, Icon, Skeleton],
  templateUrl: './session-list.html',
  styleUrl: './session-list.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class SessionList {
  private readonly api = inject(SessionApi);
  private readonly toast = inject(ToastService);

  protected readonly sessions = rxResource({ stream: () => this.api.list() });
  protected readonly others = computed(() => (this.sessions.value() ?? []).filter((s) => !s.current));

  protected readonly pending = signal<Pending | null>(null);
  protected readonly busy = signal(false);

  protected readonly confirmTitle = computed(() => {
    const p = this.pending();
    if (!p) {
      return '';
    }
    return p.kind === 'one' ? `Đăng xuất ${this.label(p.session)}?` : `Đăng xuất ${this.others().length} thiết bị khác?`;
  });

  /** Trang Tài khoản gọi sau khi đổi mật khẩu (backend đã đăng xuất mọi thiết bị khác). */
  reload(): void {
    this.sessions.reload();
  }

  protected label(session: Session): string {
    return `${session.browser} · ${session.os}`;
  }

  protected isMobile(session: Session): boolean {
    return MOBILE_OS.has(session.os);
  }

  protected signedIn(session: Session): string {
    return `Đăng nhập ${relativeDay(session.signedInAt).toLowerCase()}`;
  }

  protected lastActive(session: Session): string {
    return session.current ? 'Đang dùng' : timeAgo(session.lastActiveAt);
  }

  protected confirm(): void {
    const p = this.pending();
    if (!p || this.busy()) {
      return;
    }
    this.busy.set(true);
    if (p.kind === 'one') {
      this.api.revoke(p.session.id).subscribe({
        next: () => this.done(`Đã đăng xuất ${this.label(p.session)}`),
        error: (error: unknown) => this.failed(error),
      });
    } else {
      this.api.revokeOthers().subscribe({
        next: ({ revoked }) => this.done(revoked > 0 ? `Đã đăng xuất ${revoked} thiết bị` : 'Không còn thiết bị nào khác'),
        error: (error: unknown) => this.failed(error),
      });
    }
  }

  private done(message: string): void {
    this.busy.set(false);
    this.pending.set(null);
    this.toast.success(message);
    this.sessions.reload();
  }

  private failed(error: unknown): void {
    this.busy.set(false);
    this.pending.set(null);
    // Thiết bị đã tự đăng xuất / hết hạn trong lúc trang đang mở: danh sách cũ, tải lại là đúng.
    if (error instanceof ApiError && error.code === 'SESSION_NOT_FOUND') {
      this.toast.info('Thiết bị này đã đăng xuất trước đó');
    } else {
      this.toast.error(errorMessage(error));
    }
    this.sessions.reload();
  }
}
