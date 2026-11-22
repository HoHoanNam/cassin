import {
  BADGE_CATEGORY_THEMES,
  BADGE_METALS,
  Badge,
  BadgeCategory,
  BadgeTier,
} from '../../core/models/badge.models';

/** [THÊM] Hàm hiển thị dùng chung cho trang Thành tích và popup mở khóa. */

/** 1234 → "1.234" (dấu chấm ngăn cách hàng nghìn, kiểu Việt Nam) */
export const fmt = (n: number) => n.toLocaleString('vi-VN');

/** Cấp kế tiếp chưa đạt; null nếu đã đạt cấp cao nhất. */
export const nextTier = (b: Badge): BadgeTier | null => b.tiers.find((t) => t.earnedAt === null) ?? null;

export const isMaxed = (b: Badge) => nextTier(b) === null;

/** % tiến tới cấp kế tiếp, tính từ mốc cấp trước (cấp II: từ 30 → 300, không phải từ 0). */
export function percentToNext(b: Badge): number {
  const next = nextTier(b);
  if (!next) return 100;
  const prevTarget = next.level > 1 ? b.tiers[next.level - 2].target : 0;
  const span = next.target - prevTarget;
  return Math.max(0, Math.min(100, Math.round(((b.progress - prevTarget) / span) * 100)));
}

/** Màu kim loại của cấp (dùng cho chip "Cấp II · Bạc"). */
export const metalOf = (level: number) => BADGE_METALS[Math.max(1, Math.min(3, level)) as 1 | 2 | 3];

export const themeOf = (c: BadgeCategory) => BADGE_CATEGORY_THEMES[c];

/** Nút "đi làm ngay" cho từng nhóm: huy hiệu phải dẫn tới hành động, không chỉ để ngắm. */
export const CATEGORY_ACTIONS: Partial<Record<BadgeCategory, { label: string; link: string }>> = {
  REVIEW: { label: 'Ôn thẻ ngay', link: '/review' },
  STREAK: { label: 'Học hôm nay', link: '/today' },
  MASTERY: { label: 'Ôn thẻ ngay', link: '/review' },
  COLLECTION: { label: 'Thêm từ mới', link: '/decks' },
  QUIZ: { label: 'Làm bài ngữ pháp', link: '/grammar' },
  READING: { label: 'Làm bài đọc', link: '/reading' },
  PRACTICE: { label: 'Luyện tập', link: '/practice' },
  COMMUNITY: { label: 'Mở thư viện', link: '/library' },
};
