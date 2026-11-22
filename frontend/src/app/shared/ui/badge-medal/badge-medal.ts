import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';

import { BadgeCategory, BadgeCode, BadgeKind, BadgeLevel } from '../../../core/models/badge.models';
import { MEDAL_RATIO, MEDAL_VIEWBOX, buildMedal } from './badge-art';

/**
 * [THÊM] Huy chương SVG. Toàn bộ hình học + màu nằm trong buildMedal() (badge-art.ts);
 * component chỉ vẽ danh sách lớp nó trả về.
 *
 *   MÀU CHUNG  → khung cắt mặt, tấm đế, mũi nhọn, cánh: Đồng / Bạc / Vàng theo cấp.
 *   MÀU RIÊNG  → lòng huy chương, họa tiết, viên ngọc đỉnh: theo nhóm.
 *   Cấp I: khung + đế 3 sao · Cấp II: + mũi nhọn + ngọc nhỏ · Cấp III: + ngọc lớn + cánh + hào quang.
 *   SPECIAL: khung 12 mặt viền bạc ánh tím, hình màu vàng. Chưa đạt: xám + ổ khóa.
 *
 * Chỉ là hình trang trí (aria-hidden): tên + cấp luôn được ghi bằng chữ bên cạnh.
 */
@Component({
  selector: 'app-badge-medal',
  templateUrl: './badge-medal.html',
  styleUrl: './badge-medal.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: {
    'aria-hidden': 'true',
    '[style.--medal-size.px]': 'size()',
    '[style.--medal-ratio]': 'ratio',
  },
})
export class BadgeMedal {
  readonly code = input.required<BadgeCode>();
  readonly category = input.required<BadgeCategory>();
  readonly kind = input<BadgeKind>('TIERED');
  /** Cấp đang hiển thị: 0 = chưa đạt (xám) */
  readonly level = input<BadgeLevel>(0);
  /** Bí mật VÀ chưa đạt thì giấu hình */
  readonly hidden = input(false);
  /** Hiện tấm đế + sao */
  readonly ribbon = input(true);
  readonly size = input(96);

  protected readonly viewBox = MEDAL_VIEWBOX;
  protected readonly ratio = MEDAL_RATIO;

  protected readonly parts = computed(() =>
    buildMedal({
      code: this.code(),
      category: this.category(),
      kind: this.kind(),
      level: this.level(),
      hidden: this.hidden(),
      base: this.ribbon(),
    }),
  );
}
