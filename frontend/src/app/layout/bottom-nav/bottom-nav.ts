import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { RouterLink, RouterLinkActive } from '@angular/router';

import { StudyStatsStore } from '../../core/state/study-stats.store';
import { Icon } from '../../shared/ui/icon/icon';
import { NAV_ITEMS } from '../nav-items';

/** Thanh điều hướng dưới đáy (điện thoại, dưới 768px): nằm trong tầm ngón cái. */
@Component({
  selector: 'app-bottom-nav',
  imports: [RouterLink, RouterLinkActive, Icon],
  templateUrl: './bottom-nav.html',
  styleUrl: './bottom-nav.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class BottomNav {
  protected readonly items = NAV_ITEMS;
  protected readonly stats = inject(StudyStatsStore);
}
