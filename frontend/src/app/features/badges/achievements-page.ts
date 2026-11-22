import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { rxResource } from '@angular/core/rxjs-interop';
import { DatePipe } from '@angular/common';
import { RouterLink } from '@angular/router';

import { BadgeApi } from '../../core/api/badge.api';
import {
  BADGE_CATEGORY_LABELS,
  BADGE_CATEGORY_ORDER,
  BADGE_LEVEL_LABELS,
  BADGE_METALS,
  Badge,
  BadgeCategory,
  ROMAN,
} from '../../core/models/badge.models';
import { BadgeMedal } from '../../shared/ui/badge-medal/badge-medal';
import { EmptyState } from '../../shared/ui/empty-state/empty-state';
import { ErrorState } from '../../shared/ui/error-state/error-state';
import { Modal } from '../../shared/ui/modal/modal';
import { Skeleton } from '../../shared/ui/skeleton/skeleton';
import {
  CATEGORY_ACTIONS,
  fmt,
  isMaxed,
  metalOf,
  nextTier,
  percentToNext,
  themeOf,
} from './badge-view';

type Filter = 'ALL' | 'EARNED' | 'LOCKED';

interface CategoryChip {
  category: BadgeCategory;
  label: string;
  /** Số cấp đã đạt / tổng số cấp trong nhóm */
  earnedLevels: number;
  totalLevels: number;
}

/**
 * [THÊM] Trang Thành tích.
 *   1. Tổng quan: bao nhiêu sao (cấp) đã có, chia theo Đồng / Bạc / Vàng + 3 huy hiệu mới nhất.
 *   2. "Sắp đạt": 3 huy hiệu gần lên cấp nhất, kèm nút dẫn thẳng tới việc cần làm.
 *   3. Bộ sưu tập theo nhóm, mỗi nhóm một màu; bấm vào xem lộ trình 3 cấp.
 */
@Component({
  selector: 'app-achievements-page',
  imports: [DatePipe, RouterLink, BadgeMedal, EmptyState, ErrorState, Modal, Skeleton],
  templateUrl: './achievements-page.html',
  styleUrl: './achievements-page.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class AchievementsPage {
  private readonly api = inject(BadgeApi);

  protected readonly levelLabels = BADGE_LEVEL_LABELS;
  protected readonly categoryLabels = BADGE_CATEGORY_LABELS;
  protected readonly metals = BADGE_METALS;
  protected readonly roman = ROMAN;
  protected readonly actions = CATEGORY_ACTIONS;
  protected readonly fmt = fmt;
  protected readonly nextTier = nextTier;
  protected readonly isMaxed = isMaxed;
  protected readonly percentToNext = percentToNext;
  protected readonly metalOf = metalOf;
  protected readonly themeOf = themeOf;

  protected readonly filter = signal<Filter>('ALL');
  /** null: mọi nhóm */
  protected readonly category = signal<BadgeCategory | null>(null);
  protected readonly selected = signal<Badge | null>(null);

  protected readonly badges = rxResource({ stream: () => this.api.list() });

  /** Tổng số "sao" = tổng số cấp đã đạt trên mọi huy hiệu. */
  protected readonly earnedLevels = computed(() => this.all().reduce((sum, b) => sum + b.level, 0));
  protected readonly totalLevels = computed(() =>
    this.all().reduce((sum, b) => sum + b.tiers.length, 0),
  );
  protected readonly percent = computed(() =>
    this.totalLevels() === 0 ? 0 : Math.round((this.earnedLevels() / this.totalLevels()) * 100),
  );

  /** Số cấp đã đạt theo từng kim loại (cấp SPECIAL tính như một "huy hiệu đặc biệt"). */
  protected readonly counts = computed(() => {
    const tiered = this.all().filter((b) => b.kind === 'TIERED');
    return {
      bronze: tiered.filter((b) => b.level >= 1).length,
      silver: tiered.filter((b) => b.level >= 2).length,
      gold: tiered.filter((b) => b.level >= 3).length,
      special: this.all().filter((b) => b.kind === 'SPECIAL' && b.level > 0).length,
    };
  });

  /** 3 huy hiệu (theo cấp mới nhất) vừa đạt gần đây. */
  protected readonly recent = computed(() =>
    this.all()
      .filter((b) => b.level > 0)
      .map((b) => ({ badge: b, at: b.tiers.at(b.level - 1)!.earnedAt! }))
      .sort((a, b) => b.at.localeCompare(a.at))
      .slice(0, 3)
      .map((x) => x.badge),
  );

  /** "Sắp đạt": huy hiệu chưa tối đa, không bí mật, gần cấp kế tiếp nhất. */
  protected readonly nearest = computed(() =>
    this.all()
      .filter((b) => !b.secret && !isMaxed(b) && b.progress > 0)
      .sort((a, b) => percentToNext(b) - percentToNext(a))
      .slice(0, 3),
  );

  /** Chip lọc theo nhóm, kèm số cấp đã đạt của nhóm. */
  protected readonly chips = computed<CategoryChip[]>(() =>
    BADGE_CATEGORY_ORDER.map((category) => {
      const inGroup = this.all().filter((b) => b.category === category);
      return {
        category,
        label: BADGE_CATEGORY_LABELS[category],
        earnedLevels: inGroup.reduce((sum, b) => sum + b.level, 0),
        totalLevels: inGroup.reduce((sum, b) => sum + b.tiers.length, 0),
      };
    }).filter((c) => c.totalLevels > 0),
  );

  /** MỘT lưới chung, xếp theo thứ tự nhóm (không chia mỗi nhóm một hàng → đỡ trống trang). */
  protected readonly visible = computed(() => {
    const f = this.filter();
    const cat = this.category();
    return BADGE_CATEGORY_ORDER.flatMap((c) => this.all().filter((b) => b.category === c)).filter(
      (b) =>
        (cat === null || b.category === cat) &&
        (f === 'ALL' || (f === 'EARNED' ? b.level > 0 : !isMaxed(b))),
    );
  });

  protected toggleCategory(c: BadgeCategory): void {
    this.category.update((cur) => (cur === c ? null : c));
  }

  protected levelLabel(level: number): string {
    return BADGE_LEVEL_LABELS[Math.max(1, Math.min(3, level)) as 1 | 2 | 3];
  }

  private all(): Badge[] {
    return this.badges.hasValue() ? this.badges.value() : [];
  }
}
