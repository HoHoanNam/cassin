import { ChangeDetectionStrategy, Component, computed, inject } from '@angular/core';
import { Router } from '@angular/router';

import { BADGE_CATEGORY_LABELS, BADGE_LEVEL_LABELS, ROMAN } from '../../../core/models/badge.models';
import { BadgeStore } from '../../../core/state/badge.store';
import { BadgeMedal } from '../../../shared/ui/badge-medal/badge-medal';
import { Modal } from '../../../shared/ui/modal/modal';
import { fmt, metalOf, themeOf } from '../badge-view';

/** Vị trí + góc + độ trễ của từng mảnh giấy màu (cố định, không random để ảnh chụp ổn định). */
const CONFETTI = Array.from({ length: 16 }, (_, i) => ({
  x: (i * 37) % 100,
  delay: (i % 5) * 0.12,
  rot: (i * 47) % 360,
  tone: i % 3, // 0: màu nhóm, 1: kim loại, 2: màu nhóm sáng
}));

/**
 * [THÊM] Popup "Mở khóa huy hiệu" kiểu Duolingo. Đặt một lần trong AppShell.
 * Có 3 kiểu tiêu đề: huy hiệu mới (cấp I) · lên cấp (II, III) · huy hiệu đặc biệt.
 * Nhiều huy hiệu cùng lúc thì lần lượt từng cái ("2 / 3").
 */
@Component({
  selector: 'app-badge-unlock-dialog',
  imports: [Modal, BadgeMedal],
  templateUrl: './badge-unlock-dialog.html',
  styleUrl: './badge-unlock-dialog.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class BadgeUnlockDialog {
  protected readonly store = inject(BadgeStore);
  private readonly router = inject(Router);

  protected readonly levelLabels = BADGE_LEVEL_LABELS;
  protected readonly categoryLabels = BADGE_CATEGORY_LABELS;
  protected readonly confetti = CONFETTI;
  protected readonly fmt = fmt;

  protected readonly title = computed(() => {
    const b = this.store.current();
    if (!b) return '';
    if (b.kind === 'SPECIAL') return 'Huy hiệu đặc biệt!';
    return b.level === 1 ? 'Huy hiệu mới!' : `Lên cấp ${ROMAN[b.level]}!`;
  });

  protected readonly theme = computed(() => {
    const b = this.store.current();
    return b ? themeOf(b.category) : null;
  });

  protected readonly metal = computed(() => metalOf(this.store.current()?.level ?? 1));

  protected viewAll(): void {
    this.store.finish();
    void this.router.navigateByUrl('/achievements');
  }
}
