import { describe, expect, it } from 'vitest';

import { blankInstruction, checkBlankAnswer, highlight, parseInstruction, sentences } from './passage-text';

/** [THÊM] Các hàm xử lý chữ của bài đọc. Bản TypeScript của BlankAnswerRules phải chấm giống backend. */
describe('passage-text', () => {
  it('tách câu để admin chọn câu bằng chứng', () => {
    expect(sentences('Solar is cheap. Is it?\n\nIn 2020, about 15% used it!')).toEqual([
      'Solar is cheap.',
      'Is it?',
      'In 2020, about 15% used it!',
    ]);
  });

  it('tô gợi ý không phân biệt hoa thường, gợi ý nằm trong câu bằng chứng vẫn hiện', () => {
    const text = 'Trees need water and Care, which costs money.';
    const segments = highlight(text, [
      { text: 'Trees need water and Care, which costs money.', kind: 'evidence' },
      { text: 'care', kind: 'hint' },
    ]);
    expect(segments.map((s) => [s.text, s.kind])).toEqual([
      ['Trees need water and ', 'evidence'],
      ['Care', 'hint'],
      [', which costs money.', 'evidence'],
    ]);
    expect(segments.map((s) => s.text).join('')).toBe(text);
  });

  it('chấm định dạng câu điền từ giống BlankAnswerRules', () => {
    expect(checkBlankAnswer('WORDS', 1, ' Coal. ')).toBeNull();
    expect(checkBlankAnswer('WORDS', 1, 'black coal')).toBe('OVER_WORD_LIMIT');
    expect(checkBlankAnswer('WORDS', 2, '15 panels')).toBe('NUMBER_NOT_ALLOWED');
    expect(checkBlankAnswer('NUMBER', null, '1,000')).toBeNull();
    expect(checkBlankAnswer('NUMBER', null, 'fifteen')).toBe('NUMBER_REQUIRED');
    expect(checkBlankAnswer('WORDS_AND_OR_NUMBER', 2, '15% of homes')).toBeNull();
    expect(checkBlankAnswer('WORDS_AND_OR_NUMBER', 1, '15 20')).toBe('TOO_MANY_NUMBERS');
  });

  it('dòng hướng dẫn đi được hai chiều: luật → chữ → luật', () => {
    for (const [kind, max] of [['WORDS', 1], ['WORDS', 3], ['NUMBER', null], ['WORDS_AND_OR_NUMBER', 2]] as const) {
      expect(parseInstruction(blankInstruction(kind, max))).toEqual({ kind, maxWords: max });
    }
  });
});
