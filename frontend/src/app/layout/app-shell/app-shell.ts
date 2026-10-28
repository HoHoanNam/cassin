import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { RouterLink, RouterOutlet } from '@angular/router';

import { StudyStatsStore } from '../../core/state/study-stats.store';
import { Logo } from '../../shared/ui/logo/logo';
import { BottomNav } from '../bottom-nav/bottom-nav';
import { Sidebar } from '../sidebar/sidebar';
import { UserMenu } from '../user-menu/user-menu';

/**
 * Khung của khu vực đã đăng nhập:
 *   - Máy tính (≥ 768px): sidebar bên trái + nội dung
 *   - Điện thoại: thanh trên (logo + avatar) + nội dung + thanh điều hướng dưới đáy
 * Các trang tập trung (ôn thẻ, làm bài) KHÔNG nằm trong khung này để người học không bị phân tâm.
 */
@Component({
  selector: 'app-shell',
  imports: [RouterOutlet, RouterLink, Logo, Sidebar, BottomNav, UserMenu],
  templateUrl: './app-shell.html',
  styleUrl: './app-shell.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class AppShell {
  constructor() {
    // Mỗi lần vào khu vực đăng nhập (kể cả đổi tài khoản), lấy lại số liệu mới nhất.
    inject(StudyStatsStore).refresh();
  }
}
