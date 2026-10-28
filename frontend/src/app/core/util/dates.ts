/**
 * Xử lý ngày cho giao diện tiếng Việt.
 * Ngày từ backend có 2 dạng:
 *   - LocalDate  → "2026-10-28"            (dueDate)
 *   - Instant    → "2026-10-28T03:15:00Z"  (submittedAt, startedAt)
 */

const WEEKDAYS = ['Chủ Nhật', 'Thứ Hai', 'Thứ Ba', 'Thứ Tư', 'Thứ Năm', 'Thứ Sáu', 'Thứ Bảy'];

/** Ngày hôm nay dạng yyyy-MM-dd theo giờ máy người dùng. */
export function todayIso(): string {
  return toIsoDate(new Date());
}

export function toIsoDate(date: Date): string {
  const y = date.getFullYear();
  const m = String(date.getMonth() + 1).padStart(2, '0');
  const d = String(date.getDate()).padStart(2, '0');
  return `${y}-${m}-${d}`;
}

/** Cộng ngày cho chuỗi yyyy-MM-dd. */
export function addDays(isoDate: string, days: number): string {
  const date = parseLocalDate(isoDate);
  date.setDate(date.getDate() + days);
  return toIsoDate(date);
}

/** "Thứ Bảy, ngày 24 tháng 10 năm 2026" */
export function longVietnameseDate(date = new Date()): string {
  return `${WEEKDAYS[date.getDay()]}, ngày ${date.getDate()} tháng ${date.getMonth() + 1} năm ${date.getFullYear()}`;
}

/** "Hôm nay" / "Hôm qua" / "24/10" / "24/10/2025" (khác năm) */
export function relativeDay(value: string): string {
  const date = value.length === 10 ? parseLocalDate(value) : new Date(value);
  const diff = daysBetween(date, new Date());
  if (diff === 0) {
    return 'Hôm nay';
  }
  if (diff === 1) {
    return 'Hôm qua';
  }
  const dd = String(date.getDate()).padStart(2, '0');
  const mm = String(date.getMonth() + 1).padStart(2, '0');
  return date.getFullYear() === new Date().getFullYear() ? `${dd}/${mm}` : `${dd}/${mm}/${date.getFullYear()}`;
}

/** Câu mô tả hạn ôn tiếp theo: "Ôn lại vào ngày mai" / "Ôn lại sau 4 ngày" */
export function nextReviewLabel(dueDate: string): string {
  const days = daysBetween(new Date(), parseLocalDate(dueDate));
  if (days <= 0) {
    return 'Ôn lại hôm nay';
  }
  return days === 1 ? 'Ôn lại vào ngày mai' : `Ôn lại sau ${days} ngày`;
}

/** Số ngày (theo lịch) từ a tới b. */
function daysBetween(a: Date, b: Date): number {
  const start = Date.UTC(a.getFullYear(), a.getMonth(), a.getDate());
  const end = Date.UTC(b.getFullYear(), b.getMonth(), b.getDate());
  return Math.round((end - start) / 86_400_000);
}

/** "2026-10-28" → Date lúc 00:00 giờ máy (new Date("2026-10-28") sẽ hiểu là giờ UTC, lệch ngày). */
function parseLocalDate(isoDate: string): Date {
  const [y, m, d] = isoDate.split('-').map(Number);
  return new Date(y, m - 1, d);
}
