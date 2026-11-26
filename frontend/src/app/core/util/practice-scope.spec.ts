import { describe, expect, it } from 'vitest';

import { ALL_SCOPE } from '../models/practice.models';
import { countProblem, maxCount, scopeFromQuery, scopeToQuery, toggleDeck, toggleNotebook } from './practice-scope';

describe('practice-scope: chọn nguồn', () => {
  it('đang "Tất cả", bấm một bộ → chỉ còn bộ đó', () => {
    expect(toggleDeck(ALL_SCOPE, 3)).toEqual({ all: false, notebook: false, deckIds: [3] });
  });

  it('gộp nhiều bộ + sổ từ, bỏ chọn từng cái', () => {
    let scope = toggleDeck(ALL_SCOPE, 3);
    scope = toggleDeck(scope, 7);
    scope = toggleNotebook(scope);
    expect(scope).toEqual({ all: false, notebook: true, deckIds: [3, 7] });

    scope = toggleDeck(scope, 3);
    expect(scope.deckIds).toEqual([7]);
  });

  it('bỏ chọn nguồn cuối cùng → quay về "Tất cả"', () => {
    expect(toggleDeck(toggleDeck(ALL_SCOPE, 3), 3)).toEqual(ALL_SCOPE);
    expect(toggleNotebook(toggleNotebook(ALL_SCOPE))).toEqual(ALL_SCOPE);
  });
});

describe('practice-scope: query param', () => {
  it('đi rồi về giữ nguyên nguồn', () => {
    const scope = { all: false, notebook: true, deckIds: [3, 7] };
    expect(scopeFromQuery(scopeToQuery(scope))).toEqual(scope);
    expect(scopeFromQuery(scopeToQuery(ALL_SCOPE))).toEqual(ALL_SCOPE);
  });

  it('URL trống hoặc deckIds rác → sổ từ', () => {
    expect(scopeFromQuery({})).toEqual({ all: false, notebook: true, deckIds: [] });
    expect(scopeFromQuery({ deckIds: 'abc,-1' })).toEqual({ all: false, notebook: true, deckIds: [] });
  });
});

describe('practice-scope: số câu', () => {
  it('hợp lệ khi 1 ≤ count ≤ min(có sẵn, trần)', () => {
    expect(countProblem(35, 120, 200)).toBeNull();
    expect(countProblem(120, 120, 200)).toBeNull();
  });

  it('vượt số câu có sẵn → cảnh báo kèm con số', () => {
    expect(countProblem(121, 120, 200)).toContain('120 câu');
  });

  it('vượt trần server dù còn đủ câu', () => {
    expect(countProblem(250, 600, 200)).toContain('200');
  });

  it('rỗng, số lẻ, số âm, không có câu nào', () => {
    expect(countProblem(null, 10, 200)).not.toBeNull();
    expect(countProblem(2.5, 10, 200)).not.toBeNull();
    expect(countProblem(0, 10, 200)).not.toBeNull();
    expect(countProblem(5, 0, 200)).toContain('chưa có câu');
  });

  it('MAX = min(có sẵn, trần)', () => {
    expect(maxCount(120, 200)).toBe(120);
    expect(maxCount(600, 200)).toBe(200);
  });
});
