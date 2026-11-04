/**
 * [V10] Câu ví dụ đánh dấu từ đích bằng [ ], giống ExampleMarkup ở backend:
 *   "Her English has [improved] a lot."  → từ đích "improved" (đúng dạng đã chia trong câu)
 *
 * Dấu [ ] dùng cho bài luyện "Điền từ" (che chỗ đánh dấu). Khi chỉ để đọc thì bỏ dấu đi.
 */
const MARK = /\[([^\[\]]*)\]/;

export interface ExampleParts {
  before: string;
  target: string;
  after: string;
}

/** Tách câu có đánh dấu thành 3 phần. Không có dấu (hoặc dấu rỗng) → null. */
export function parseExample(raw: string): ExampleParts | null {
  const match = MARK.exec(raw);
  if (!match || match[1].trim() === '') {
    return null;
  }
  return {
    before: raw.slice(0, match.index),
    target: match[1],
    after: raw.slice(match.index + match[0].length),
  };
}

/** Bỏ dấu [ ] để hiển thị: "Her English has [improved] a lot." → "Her English has improved a lot." */
export function plainExample(raw: string): string {
  const parts = parseExample(raw);
  return parts ? parts.before + parts.target + parts.after : raw;
}

/**
 * Lỗi đánh dấu (giống ExampleMarkup.validate): null nếu hợp lệ.
 * Không có dấu nào là hợp lệ (câu chỉ để đọc, không dùng cho bài điền từ).
 */
export function exampleMarkupError(raw: string): string | null {
  if (!raw.includes('[') && !raw.includes(']')) {
    return null;
  }
  const match = MARK.exec(raw);
  if (!match || match[1].trim() === '') {
    return 'Đánh dấu sai, hãy dùng dạng [từ]';
  }
  const rest = raw.slice(0, match.index) + raw.slice(match.index + match[0].length);
  if (rest.includes('[') || rest.includes(']')) {
    return 'Mỗi câu chỉ đánh dấu MỘT từ';
  }
  return null;
}
