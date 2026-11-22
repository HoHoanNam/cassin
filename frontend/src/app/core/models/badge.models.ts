/**
 * [THÊM] Huy hiệu (gamification). Khớp 1-1 với DTO dự kiến của module com.cassin.badge ở backend.
 *
 * HAI LOẠI HUY HIỆU (tham khảo Duolingo Achievements, Microsoft Learn, 3-star system):
 *
 *   TIERED  - một huy hiệu, 3 cấp: I (Đồng) → II (Bạc) → III (Vàng).
 *             Thay vì 3 huy hiệu rời rạc "100 lượt / 500 lượt / 2000 lượt" làm rối bộ sưu tập,
 *             người học thấy MỘT huy hiệu lớn dần, luôn có mục tiêu kế tiếp.
 *   SPECIAL - huy hiệu một lần, cho khoảnh khắc đặc biệt (quay lại sau kỳ nghỉ, thắng từ "khó nhằn"...).
 *             Có thể là bí mật: chỉ lộ tên khi đã đạt.
 *
 * Vòng đời một cấp: LOCKED ──(progress >= target)──▶ EARNED (seenAt = null) ──(xem popup)──▶ EARNED (đã xem)
 * Backend là nơi DUY NHẤT cấp huy hiệu (frontend chỉ hiển thị).
 */

export type BadgeKind = 'TIERED' | 'SPECIAL';

/** Cấp đã đạt: 0 = chưa đạt cấp nào. Huy hiệu SPECIAL chỉ có cấp 1. */
export type BadgeLevel = 0 | 1 | 2 | 3;

/** Nhóm huy hiệu: mỗi nhóm một màu riêng (xem BADGE_CATEGORY_THEMES). */
export type BadgeCategory =
  | 'REVIEW'
  | 'STREAK'
  | 'MASTERY'
  | 'COLLECTION'
  | 'QUIZ'
  | 'READING'
  | 'PRACTICE'
  | 'COMMUNITY'
  | 'SPECIAL';

/** Mã huy hiệu: khóa chính của bảng badge_definitions, không đổi sau khi phát hành. */
export type BadgeCode =
  // Có cấp
  | 'DILIGENT'
  | 'ON_FIRE'
  | 'MEMORY_KEEPER'
  | 'COLLECTOR'
  | 'PERFECTIONIST'
  | 'GRAMMARIAN'
  | 'BOOKWORM'
  | 'SELF_RELIANT'
  | 'WORDSMITH'
  | 'SHARP_EYE'
  // Đặc biệt
  | 'ALL_ROUNDER'
  | 'NEVER_GIVE_UP'
  | 'COMEBACK'
  | 'NIGHT_OWL'
  | 'EARLY_BIRD';

/** Một cấp của huy hiệu. */
export interface BadgeTier {
  level: 1 | 2 | 3;
  target: number;
  /** null: chưa đạt cấp này */
  earnedAt: string | null;
}

/**
 * Khớp BadgeResponse: GET /api/badges.
 * Huy hiệu BÍ MẬT chưa đạt: server che name / description ("???"), progress = 0.
 */
export interface Badge {
  code: BadgeCode;
  kind: BadgeKind;
  name: string;
  /** Mô tả chung, ví dụ "Trả lời thẻ ôn tập" */
  description: string;
  /** Đơn vị đếm hiện cạnh số, ví dụ "lượt", "ngày" */
  unit: string;
  category: BadgeCategory;
  secret: boolean;
  /** Cấp cao nhất đã đạt (0 = chưa đạt) */
  level: BadgeLevel;
  /** Giá trị hiện tại của chỉ số (chưa chặn trên) */
  progress: number;
  /** Các cấp, tăng dần. SPECIAL: đúng 1 phần tử. */
  tiers: BadgeTier[];
}

/** Khớp EarnedBadgeResponse: GET /api/badges/unseen. Mỗi CẤP vừa đạt là một dòng. */
export interface EarnedBadge {
  code: BadgeCode;
  kind: BadgeKind;
  name: string;
  description: string;
  unit: string;
  category: BadgeCategory;
  level: 1 | 2 | 3;
  /** Mốc của cấp vừa đạt */
  target: number;
  /** Số cấp tối đa (TIERED: 3, SPECIAL: 1) */
  maxLevel: number;
  earnedAt: string;
}

/** Một cặp (mã, cấp) đã xem popup. */
export interface SeenBadge {
  code: BadgeCode;
  level: number;
}

/** Khớp MarkSeenRequest: POST /api/badges/seen */
export interface MarkSeenRequest {
  items: SeenBadge[];
}

export const BADGE_CATEGORY_LABELS: Record<BadgeCategory, string> = {
  REVIEW: 'Ôn thẻ',
  STREAK: 'Chuỗi ngày học',
  MASTERY: 'Thuộc từ',
  COLLECTION: 'Kho từ',
  QUIZ: 'Ngữ pháp',
  READING: 'Đọc hiểu',
  PRACTICE: 'Luyện tập',
  COMMUNITY: 'Cộng đồng',
  SPECIAL: 'Đặc biệt',
};

/** Thứ tự nhóm trên trang Thành tích. */
export const BADGE_CATEGORY_ORDER: BadgeCategory[] = [
  'REVIEW',
  'STREAK',
  'MASTERY',
  'COLLECTION',
  'QUIZ',
  'READING',
  'PRACTICE',
  'COMMUNITY',
  'SPECIAL',
];

/** Tên cấp: số La Mã + kim loại. */
export const BADGE_LEVEL_LABELS: Record<1 | 2 | 3, string> = {
  1: 'Cấp I · Đồng',
  2: 'Cấp II · Bạc',
  3: 'Cấp III · Vàng',
};

export const ROMAN = ['', 'I', 'II', 'III'] as const;

/* ============================================================
 * BẢNG MÀU HUY HIỆU
 * ============================================================
 * Hai lớp màu, tách vai trò rõ ràng:
 *   1. MÀU CHUNG (BADGE_METALS + BADGE_NEUTRALS): viền kim loại cho biết CẤP, dùng cho mọi nhóm.
 *      Người học nhìn viền là biết huy hiệu "to" cỡ nào, không cần đọc chữ.
 *   2. MÀU RIÊNG (BADGE_CATEGORY_THEMES): mặt huy chương cho biết NHÓM. 9 sắc độ cách đều
 *      trên vòng màu (cam 25°, hổ phách 40°, xanh lá 145°, ngọc 175°, xanh dương 222°,
 *      tím 262°, tím đêm 245°, hồng tím 318°, đỏ hồng 350°) để hai nhóm cạnh nhau không lẫn.
 * Mỗi màu có 3 tông: light (mảng sáng), base (mặt chính), dark (bóng đổ, viền trong).
 * Huy hiệu chưa đạt KHÔNG dùng màu nhóm: chuyển sang xám giấy (BADGE_NEUTRALS.locked).
 */

export interface Tone {
  light: string;
  base: string;
  dark: string;
}

/** Màu riêng từng nhóm + họa tiết nền mặt huy chương. */
export interface CategoryTheme extends Tone {
  pattern:
    'stripes' | 'embers' | 'rings' | 'dots' | 'checks' | 'lines' | 'zigzag' | 'rays' | 'stars';
}

export const BADGE_CATEGORY_THEMES: Record<BadgeCategory, CategoryTheme> = {
  REVIEW: { light: '#8DB4FF', base: '#2F6BED', dark: '#1B47B3', pattern: 'stripes' },
  STREAK: { light: '#FFB27A', base: '#F26B1D', dark: '#B4460B', pattern: 'embers' },
  MASTERY: { light: '#C2A6FF', base: '#7B4FE0', dark: '#5130A8', pattern: 'rings' },
  COLLECTION: { light: '#7FDCCF', base: '#119C8E', dark: '#0A6B62', pattern: 'dots' },
  QUIZ: { light: '#FF9BAE', base: '#E0315A', dark: '#A31B3D', pattern: 'checks' },
  READING: { light: '#93DCA6', base: '#2E9B52', dark: '#1D6B37', pattern: 'lines' },
  PRACTICE: { light: '#F2A0E6', base: '#C2309F', dark: '#88206F', pattern: 'zigzag' },
  COMMUNITY: { light: '#FFD58A', base: '#D98A0B', dark: '#9A5F05', pattern: 'rays' },
  SPECIAL: { light: '#9AA3F5', base: '#3B3F9E', dark: '#23256A', pattern: 'stars' },
};

/** Màu chung: kim loại cho viền theo cấp. */
export const BADGE_METALS: Record<1 | 2 | 3, Tone> = {
  1: { light: '#F2C29A', base: '#C67B45', dark: '#8A4E25' }, // Đồng
  2: { light: '#F1F4F9', base: '#AEB8CA', dark: '#6F7B93' }, // Bạc
  3: { light: '#FFE58A', base: '#F2B40C', dark: '#B07F00' }, // Vàng
};

/** Màu chung: trạng thái khóa, chữ trên huy chương, ánh sáng. */
export const BADGE_NEUTRALS = {
  locked: { light: '#F3F1F7', base: '#DEDAE8', dark: '#B9B3CB' } satisfies Tone,
  lockedInk: '#A29CB8',
  glyph: '#FFFFFF',
  shine: '#FFFFFF',
  ink: '#23264A',
};
