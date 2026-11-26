import { ALL_SCOPE, PracticeScope } from '../models/practice.models';

/**
 * [THÊM] Logic thuần của trang Luyện tập (không phụ thuộc Angular → test thẳng bằng vitest):
 *   - chọn nguồn: "Tất cả" / sổ từ / nhiều bộ
 *   - chuyển nguồn ↔ query param của /practice/session
 *   - kiểm tra số câu TRƯỚC khi gọi backend
 */

// ===================== Chọn nguồn =====================

/** Bấm chip "Sổ từ của tôi": đang "Tất cả" thì chuyển sang chỉ sổ từ; bỏ chọn hết thì về "Tất cả". */
export function toggleNotebook(scope: PracticeScope): PracticeScope {
  if (scope.all) {
    return { all: false, notebook: true, deckIds: [] };
  }
  return normalize({ ...scope, notebook: !scope.notebook });
}

/** Bấm chip một bộ: đang "Tất cả" thì chỉ chọn bộ đó; bấm lại thì bỏ chọn. */
export function toggleDeck(scope: PracticeScope, deckId: number): PracticeScope {
  if (scope.all) {
    return { all: false, notebook: false, deckIds: [deckId] };
  }
  const deckIds = scope.deckIds.includes(deckId)
    ? scope.deckIds.filter((id) => id !== deckId)
    : [...scope.deckIds, deckId];
  return normalize({ ...scope, deckIds });
}

/** Không chọn gì → "Tất cả" (để nút Bắt đầu không bao giờ rơi vào trạng thái "chưa có nguồn"). */
function normalize(scope: PracticeScope): PracticeScope {
  return !scope.notebook && scope.deckIds.length === 0 ? ALL_SCOPE : scope;
}

// ===================== Query param =====================

export interface ScopeQuery {
  all?: string;
  deckIds?: string;
  notebook?: string;
}

/** { all: '1' } | { deckIds: '3,7', notebook: '1' }: chỉ ghi tham số đang bật cho URL gọn. */
export function scopeToQuery(scope: PracticeScope): ScopeQuery {
  if (scope.all) {
    return { all: '1' };
  }
  return {
    deckIds: scope.deckIds.length > 0 ? scope.deckIds.join(',') : undefined,
    notebook: scope.notebook ? '1' : undefined,
  };
}

/** Đọc ngược từ URL. Số rác bị bỏ qua; URL trống = sổ từ (giống backend khi không có tham số). */
export function scopeFromQuery(query: ScopeQuery): PracticeScope {
  if (query.all === '1' || query.all === 'true') {
    return ALL_SCOPE;
  }
  const deckIds = (query.deckIds ?? '')
    .split(',')
    .map((part) => Number(part.trim()))
    .filter((id) => Number.isInteger(id) && id > 0);
  const notebook = query.notebook === '1' || query.notebook === 'true';
  return { all: false, notebook: notebook || deckIds.length === 0, deckIds: [...new Set(deckIds)] };
}

// ===================== Số câu =====================

/**
 * Lý do KHÔNG được bắt đầu với số câu này (null = hợp lệ).
 * Kiểm tra đúng các luật backend sẽ kiểm tra (INVALID_COUNT, PRACTICE_COUNT_EXCEEDS) để người học
 * thấy cảnh báo ngay khi nhập, thay vì vào trang luyện rồi mới gặp lỗi 400.
 */
export function countProblem(count: number | null, available: number, cap: number): string | null {
  if (available === 0) {
    return 'Phạm vi đã chọn chưa có câu nào cho chế độ này. Hãy chọn thêm bộ từ hoặc đổi chế độ.';
  }
  if (count === null || Number.isNaN(count)) {
    return 'Nhập số câu muốn luyện.';
  }
  if (!Number.isInteger(count) || count < 1) {
    return 'Số câu phải là số nguyên từ 1 trở lên.';
  }
  if (count > available) {
    return `Phạm vi đã chọn chỉ có ${available} câu. Giảm số câu, bấm MAX hoặc chọn thêm bộ từ.`;
  }
  if (count > cap) {
    return `Mỗi lượt tối đa ${cap} câu.`;
  }
  return null;
}

/** Số câu lớn nhất được phép = min(có sẵn, trần server). */
export function maxCount(available: number, cap: number): number {
  return Math.min(available, cap);
}
