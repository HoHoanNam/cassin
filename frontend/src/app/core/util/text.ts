/** Chữ cái đầu để làm avatar: "Nguyễn Nam" → "N" */
export function initial(name: string | null | undefined): string {
  const trimmed = (name ?? '').trim();
  return trimmed ? trimmed.charAt(0).toUpperCase() : '?';
}

/** Chuỗi rỗng / toàn khoảng trắng → null (giống blankToNull ở backend). */
export function blankToNull(value: string | null | undefined): string | null {
  const trimmed = (value ?? '').trim();
  return trimmed ? trimmed : null;
}

/** "A, B, C" theo thứ tự đáp án */
export const OPTION_LETTERS = ['A', 'B', 'C', 'D'] as const;
