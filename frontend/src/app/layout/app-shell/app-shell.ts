import { ChangeDetectionStrategy, Component, DestroyRef, inject } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { NavigationEnd, Router, RouterLink, RouterOutlet } from '@angular/router';
import { filter } from 'rxjs';

import { BadgeStore } from '../../core/state/badge.store';
import { StudyStatsStore } from '../../core/state/study-stats.store';
import { BadgeUnlockDialog } from '../../features/badges/badge-unlock-dialog/badge-unlock-dialog';
import { Logo } from '../../shared/ui/logo/logo';
import { BottomNav } from '../bottom-nav/bottom-nav';
import { Sidebar } from '../sidebar/sidebar';
import { UserMenu } from '../user-menu/user-menu';

/**
 * Khung của khu vực đã đăng nhập:
 *   - Máy tính (≥ 768px): sidebar bên trái + nội dung
 *   - Điện thoại: thanh trên (logo + avatar) + nội dung + thanh điều hướng dưới đáy
 * Các trang tập trung (ôn thẻ, làm bài) KHÔNG nằm trong khung này để người học không bị phân tâm.
 *
 * Vùng nội dung dùng nền vở ô li (class "notebook") cho mọi trang bên trong:
 * từng trang KHÔNG tự vẽ nền nữa, chỉ cần dùng class "page-pad" để chữ nằm bên phải đường lề đỏ.
 */
@Component({
  selector: 'app-shell',
  imports: [RouterOutlet, RouterLink, Logo, Sidebar, BottomNav, UserMenu, BadgeUnlockDialog],
  templateUrl: './app-shell.html',
  styleUrl: './app-shell.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class AppShell {
  constructor() {
    // Mỗi lần vào khu vực đăng nhập (kể cả đổi tài khoản), lấy lại số liệu mới nhất.
    inject(StudyStatsStore).refresh();

    // [THÊM] Hỏi huy hiệu mới khi vào khung và sau MỖI lần chuyển trang trong khung.
    // Ôn thẻ / làm bài là trang tập trung (ngoài khung): học xong quay về → popup hiện đúng lúc.
    const badges = inject(BadgeStore);
    badges.check();
    inject(Router)
      .events.pipe(
        filter((e) => e instanceof NavigationEnd),
        takeUntilDestroyed(inject(DestroyRef)),
      )
      .subscribe(() => badges.check());
  }
}
