import { AnswerKind, RejectedReason } from '../models/passage.models';

/**
 * [THÊM] Xử lý chữ của bài đọc, dùng chung cho trang làm bài, trang kết quả, trang soạn bài và mock.
 */

/** Hai lần xuống dòng = sang đoạn mới (giống cách admin nhập ở backend). */
export function paragraphs(content: string): string[] {
  return content
    .split(/\n\s*\n/)
    .map((p) => p.trim())
    .filter(Boolean);
}

/** "A", "B", "C"... để đánh nhãn đoạn kiểu IELTS (Paragraph A). */
export function paragraphLabel(index: number): string {
  return String.fromCharCode(65 + index);
}

/**
 * Tách bài thành từng câu, để admin CHỌN câu bằng chứng thay vì gõ tay
 * (backend yêu cầu bằng chứng phải trích NGUYÊN VĂN, chọn từ danh sách thì không bao giờ sai).
 */
export function sentences(content: string): string[] {
  const result: string[] = [];
  for (const paragraph of paragraphs(content)) {
    for (const match of paragraph.matchAll(/[^.!?]+(?:[.!?]+["'”’)\]]*|$)/g)) {
      const sentence = match[0].trim();
      if (sentence) {
        result.push(sentence);
      }
    }
  }
  return result;
}

export function countWords(text: string): number {
  return text
    .trim()
    .split(/\s+/)
    .filter((token) => /[\p{L}\p{N}]/u.test(token)).length;
}

// ───────────── Tô sáng trong bài ─────────────

export type MarkKind = 'hint' | 'evidence';

export interface Mark {
  text: string;
  kind: MarkKind;
}

export interface Segment {
  text: string;
  kind: MarkKind | null;
}

/**
 * Cắt một đoạn văn thành các mảnh, mảnh nào trùng với dấu đánh (gợi ý / bằng chứng) thì gắn kind để tô màu.
 *   - gợi ý: không phân biệt hoa thường ("Alternative" vẫn khớp "alternative")
 *   - bằng chứng: khớp nguyên văn
 * Gợi ý nằm TRONG câu bằng chứng thì gợi ý được ưu tiên (vàng nổi trên nền xanh của câu).
 * Cách làm: gắn nhãn cho TỪNG ký tự, rồi gộp các ký tự liền nhau cùng nhãn thành một mảnh.
 */
export function highlight(text: string, marks: Mark[]): Segment[] {
  const kinds: (MarkKind | null)[] = new Array(text.length).fill(null);
  const lower = text.toLowerCase();
  // Bằng chứng trước, gợi ý sau → gợi ý ghi đè lên bằng chứng.
  const ordered = [...marks].sort((a, b) => (a.kind === b.kind ? 0 : a.kind === 'evidence' ? -1 : 1));
  for (const mark of ordered) {
    const needle = mark.text.trim();
    if (!needle) {
      continue;
    }
    const haystack = mark.kind === 'hint' ? lower : text;
    const target = mark.kind === 'hint' ? needle.toLowerCase() : needle;
    for (let at = haystack.indexOf(target); at >= 0; at = haystack.indexOf(target, at + target.length)) {
      kinds.fill(mark.kind, at, at + target.length);
    }
  }

  const segments: Segment[] = [];
  for (let i = 0; i < text.length; i++) {
    const last = segments[segments.length - 1];
    if (last && last.kind === kinds[i]) {
      last.text += text[i];
    } else {
      segments.push({ text: text[i], kind: kinds[i] });
    }
  }
  return segments;
}

// ───────────── Luật câu điền từ (bản TypeScript của BlankAnswerRules) ─────────────
// Backend mới là nơi chấm. Bản này chỉ để: cảnh báo học viên TRƯỚC khi nộp, mock chấm giống backend,
// và trang soạn bài hiện dòng hướng dẫn xem trước.

const NUMBER = /^\d+(?:[.,]\d+)*%?$/;
const NUMBER_WORDS = ['', 'ONE', 'TWO', 'THREE'];

export function normalizeAnswer(raw: string | null | undefined): string {
  if (!raw) {
    return '';
  }
  return raw
    .replace(/[‘’]/g, "'")
    .replace(/[“”]/g, '"')
    .trim()
    .replace(/\s+/g, ' ')
    .toLowerCase()
    .replace(/^[^\p{L}\p{N}%]+|[^\p{L}\p{N}%]+$/gu, '');
}

/** null = đúng định dạng; còn lại là lý do sai (giống BlankAnswerRules.check). */
export function checkBlankAnswer(kind: AnswerKind, maxWords: number | null, raw: string): RejectedReason | null {
  const normalized = normalizeAnswer(raw);
  const tokens = normalized ? normalized.split(' ') : [];
  const numbers = tokens.filter((t) => NUMBER.test(t)).length;
  const words = tokens.length - numbers;
  const max = maxWords ?? 0;
  switch (kind) {
    case 'WORDS':
      return numbers > 0 ? 'NUMBER_NOT_ALLOWED' : words > max ? 'OVER_WORD_LIMIT' : null;
    case 'NUMBER':
      return words > 0 || numbers === 0 ? 'NUMBER_REQUIRED' : numbers > 1 ? 'TOO_MANY_NUMBERS' : null;
    case 'WORDS_AND_OR_NUMBER':
      return numbers > 1 ? 'TOO_MANY_NUMBERS' : words > max ? 'OVER_WORD_LIMIT' : null;
  }
}

/** "NO MORE THAN TWO WORDS AND/OR A NUMBER" (giống BlankAnswerRules.instruction). */
export function blankInstruction(kind: AnswerKind, maxWords: number | null): string {
  const max = maxWords ?? 1;
  switch (kind) {
    case 'NUMBER':
      return 'A NUMBER';
    case 'WORDS':
      return max === 1 ? 'ONE WORD ONLY' : `NO MORE THAN ${NUMBER_WORDS[max]} WORDS`;
    case 'WORDS_AND_OR_NUMBER':
      return max === 1 ? 'ONE WORD AND/OR A NUMBER' : `NO MORE THAN ${NUMBER_WORDS[max]} WORDS AND/OR A NUMBER`;
  }
}

/**
 * Học viên chỉ nhận dòng hướng dẫn (QuestionView.instruction), không nhận answerKind / maxWords.
 * Đọc ngược dòng hướng dẫn ra luật để cảnh báo trước khi nộp. Không hiểu được → null (không cảnh báo).
 */
export function parseInstruction(instruction: string | undefined): { kind: AnswerKind; maxWords: number | null } | null {
  if (!instruction) {
    return null;
  }
  if (instruction === 'A NUMBER') {
    return { kind: 'NUMBER', maxWords: null };
  }
  const max = NUMBER_WORDS.findIndex((w, i) => i > 0 && instruction.includes(`${w} WORD`));
  if (max < 1) {
    return null;
  }
  return { kind: instruction.includes('AND/OR A NUMBER') ? 'WORDS_AND_OR_NUMBER' : 'WORDS', maxWords: max };
}
