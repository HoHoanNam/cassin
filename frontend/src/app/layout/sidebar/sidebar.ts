import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { RouterLink, RouterLinkActive } from '@angular/router';

import { StudyStatsStore } from '../../core/state/study-stats.store';
import { Icon } from '../../shared/ui/icon/icon';
import { Logo } from '../../shared/ui/logo/logo';
import { NAV_ITEMS } from '../nav-items';
import { UserMenu } from '../user-menu/user-menu';

/** Thanh điều hướng bên trái (màn hình từ 768px trở lên). */
@Component({
  selector: 'app-sidebar',
  imports: [RouterLink, RouterLinkActive, Icon, Logo, UserMenu],
  templateUrl: './sidebar.html',
  styleUrl: './sidebar.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class Sidebar {
  protected readonly items = NAV_ITEMS;
  protected readonly stats = inject(StudyStatsStore);
}
