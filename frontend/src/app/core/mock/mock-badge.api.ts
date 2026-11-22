import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { BadgeApi } from '../api/badge.api';
import { Badge, BadgeLevel, EarnedBadge, SeenBadge } from '../models/badge.models';
import { BADGE_CATALOG, BadgeDefinition } from './mock-badge-catalog';
import { DbUserBadge, MockDb } from './mock-db';
import { MockSession } from './mock-session';
import { simulate } from './mock-utils';

/**
 * [THÊM] Giả lập BadgeService của backend.
 *
 * Backend cấp huy hiệu NGAY sau mỗi sự kiện học tập (listener AFTER_COMMIT).
 * Mock không có sự kiện, nên trước mỗi lần đọc thì chạy award() một lượt:
 * cấp mọi CẤP đã đủ điều kiện mà chưa có dòng trong user_badges. Nhìn từ frontend là như nhau.
 *
 * Luật giống backend:
 *   1. đã đạt thì giữ mãi (chuỗi ngày bị đứt cũng không mất cấp)
 *   2. mỗi (user, code, level) chỉ một dòng          → không cấp trùng
 *   3. nhảy nhiều cấp một lúc (vd. nhập 120 từ) → cấp đủ từng cấp, popup hiện cấp cao nhất trước
 *   4. bí mật chưa đạt bị che tên / mô tả / tiến độ
 *   5. markSeen bỏ qua cặp chưa đạt hoặc lạ          → gọi lại bao nhiêu lần cũng được
 */
@Injectable()
export class MockBadgeApi extends BadgeApi {
  private readonly db = inject(MockDb);
  private readonly session = inject(MockSession);

  list(): Observable<Badge[]> {
    return simulate(() => {
      const userId = this.session.userId();
      this.award(userId);
      return BADGE_CATALOG.map((def) => this.toBadge(def, userId));
    });
  }

  unseen(): Observable<EarnedBadge[]> {
    return simulate(() => {
      const userId = this.session.userId();
      this.award(userId);
      const mine = this.db.userBadges.filter((ub) => ub.userId === userId);
      // Cùng một huy hiệu nhảy nhiều cấp: chỉ hiện cấp cao nhất (các cấp dưới coi như đã thấy theo).
      const top = mine.filter(
        (ub) => ub.seenAt === null && !mine.some((o) => o.code === ub.code && o.level > ub.level),
      );
      return top
        .sort((a, b) => a.earnedAt.localeCompare(b.earnedAt) || a.code.localeCompare(b.code))
        .flatMap((ub) => {
          const def = BADGE_CATALOG.find((d) => d.code === ub.code);
          return def ? [toEarned(def, ub)] : [];
        });
    });
  }

  markSeen(items: SeenBadge[]): Observable<void> {
    return simulate(() => {
      const userId = this.session.userId();
      const now = new Date().toISOString();
      for (const ub of this.db.userBadges) {
        // Đã xem cấp N thì mọi cấp <= N của huy hiệu đó cũng tính là đã xem.
        if (
          ub.userId === userId &&
          ub.seenAt === null &&
          items.some((i) => i.code === ub.code && i.level >= ub.level)
        ) {
          ub.seenAt = now;
        }
      }
      this.db.save();
    });
  }

  /** = BadgeAwardService.evaluate(userId) ở backend. */
  private award(userId: number): void {
    const now = new Date().toISOString();
    let changed = false;
    for (const def of BADGE_CATALOG) {
      const value = def.progress(this.db, userId);
      def.targets.forEach((target, i) => {
        const level = i + 1;
        const owned = this.db.userBadges.some(
          (ub) => ub.userId === userId && ub.code === def.code && ub.level === level,
        );
        if (!owned && value >= target) {
          this.db.userBadges.push({ userId, code: def.code, level, earnedAt: now, seenAt: null });
          changed = true;
        }
      });
    }
    if (changed) {
      this.db.save();
    }
  }

  private toBadge(def: BadgeDefinition, userId: number): Badge {
    const rows = this.db.userBadges.filter((ub) => ub.userId === userId && ub.code === def.code);
    const level = Math.max(0, ...rows.map((r) => r.level)) as BadgeLevel;
    const tiers = def.targets.map((target, i) => ({
      level: (i + 1) as 1 | 2 | 3,
      target,
      earnedAt: rows.find((r) => r.level === i + 1)?.earnedAt ?? null,
    }));
    if (def.secret && level === 0) {
      return {
        code: def.code,
        kind: def.kind,
        name: '???',
        description: 'Huy hiệu bí mật. Cứ học đi, rồi sẽ biết.',
        unit: '',
        category: def.category,
        secret: true,
        level: 0,
        progress: 0,
        tiers: tiers.map((t) => ({ ...t, target: 1 })),
      };
    }
    return {
      code: def.code,
      kind: def.kind,
      name: def.name,
      description: def.description,
      unit: def.unit,
      category: def.category,
      secret: def.secret,
      level,
      progress: def.progress(this.db, userId),
      tiers,
    };
  }
}

function toEarned(def: BadgeDefinition, ub: DbUserBadge): EarnedBadge {
  return {
    code: def.code,
    kind: def.kind,
    name: def.name,
    description: def.description,
    unit: def.unit,
    category: def.category,
    level: ub.level as 1 | 2 | 3,
    target: def.targets[ub.level - 1],
    maxLevel: def.targets.length,
    earnedAt: ub.earnedAt,
  };
}
