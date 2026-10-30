import { ChangeDetectionStrategy, Component } from '@angular/core';
import { RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';

import { Icon, IconName } from '../../../shared/ui/icon/icon';

interface AdminTab {
  label: string;
  icon: IconName;
  link: string;
  /** Nhãn ngắn cho điện thoại: 3 tab phải vừa một hàng ở màn 360px, không phải cuộn ngang mới thấy */
  short: string;
  /** Tab "Tổng quan" (/admin) chỉ sáng khi đúng /admin, không sáng khi đang ở /admin/decks */
  exact: boolean;
}

/**
 * Khung chung của khu vực quản trị: tiêu đề + thanh tab + nội dung trang con.
 * Nằm TRONG AppShell (vẫn có sidebar), chỉ thêm một tầng điều hướng riêng cho admin.
 */
@Component({
  selector: 'app-admin-layout',
  imports: [RouterOutlet, RouterLink, RouterLinkActive, Icon],
  templateUrl: './admin-layout.html',
  styleUrl: './admin-layout.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class AdminLayout {
  protected readonly tabs: AdminTab[] = [
    { label: 'Tổng quan', short: 'Tổng quan', icon: 'chart', link: '/admin', exact: true },
    { label: 'Bộ từ hệ thống', short: 'Bộ từ', icon: 'decks', link: '/admin/decks', exact: false },
    { label: 'Ngữ pháp', short: 'Ngữ pháp', icon: 'grammar', link: '/admin/grammar', exact: false },
  ];
}
