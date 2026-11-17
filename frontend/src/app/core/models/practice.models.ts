import { PartOfSpeech } from './deck.models';

/**
 * [THÊM] Luyện tập theo câu ví dụ + Sổ lỗi sai. Khớp 1-1 với package com.cassin.practice.dto ở backend.
 */

/** Khớp enum PracticeMode. FLASHCARD là lật thẻ (chấm ở ReviewApi), không luyện qua PracticeApi. */
export type PracticeMode = 'FLASHCARD' | 'FILL_BLANK' | 'SENTENCE_BUILD';

/** Khớp FillBlankQuestion. KHÔNG có đáp án: server chấm. */
export interface FillBlankQuestion {
  senseId: number;
  exampleIndex: number;
  /** Câu đã che: "Her English has ____ a lot." */
  sentence: string;
  partOfSpeech: PartOfSpeech;
  definition: string;
  /** Gợi ý chữ cái đầu, viết thường */
  firstLetter: string;
  answerLength: number;
}

/** Khớp SentenceBuildQuestion. tokens đã xáo trộn, dấu câu dính theo chữ ("lot."). */
export interface SentenceBuildQuestion {
  senseId: number;
  exampleIndex: number;
  term: string;
  definition: string;
  tokens: string[];
}

/** Khớp PracticeAnswer: POST /api/practice/answers */
export interface PracticeAnswer {
  senseId: number;
  exampleIndex: number;
  mode: Exclude<PracticeMode, 'FLASHCARD'>;
  /** Điền từ: từ đã gõ. Xếp câu: các mảnh nối nhau bằng 1 dấu cách. */
  answer: string;
  durationMs: number;
}

/** Khớp PracticeResult */
export interface PracticeResult {
  correct: boolean;
  /** Đáp án đúng (Điền từ: từ; Xếp câu: cả câu) */
  expected: string;
  /** Gợi ý khi sai "gần đúng" (gõ từ gốc thay vì dạng đã chia). null nếu không có. */
  hint: string | null;
}

/** Khớp ModeStat */
export interface ModeStat {
  mode: PracticeMode;
  total: number;
  correct: number;
}

/** Khớp PracticeStatsResponse.DayStat */
export interface DayStat {
  /** yyyy-MM-dd */
  date: string;
  total: number;
  correct: number;
}

/** Khớp PracticeStatsResponse: GET /api/practice/stats?days= */
export interface PracticeStats {
  total: number;
  correct: number;
  /** 0.0 → 1.0 */
  accuracy: number;
  byMode: ModeStat[];
  /** ĐỦ mọi ngày trong khoảng, kể cả ngày không luyện */
  daily: DayStat[];
}

/** Khớp MistakeItem: một NGHĨA hay làm sai gần đây */
export interface MistakeItem {
  senseId: number;
  wordId: number;
  term: string;
  partOfSpeech: PartOfSpeech;
  definition: string;
  wrongCount: number;
  lastWrongAt: string;
}

/** Khớp các hằng số ở backend */
export const PRACTICE_LIMITS = {
  /** PracticeService.MAX_QUESTIONS */
  maxQuestions: 30,
  /** PracticeService.MIN_TOKENS / MAX_TOKENS: câu dùng cho Xếp câu */
  minTokens: 5,
  maxTokens: 15,
  /** @Max(600_000) của PracticeAnswer.durationMs */
  maxDurationMs: 600_000,
  /** MistakeService.MAX_DAYS / MAX_LIMIT */
  mistakeMaxDays: 30,
  mistakeMaxLimit: 50,
} as const;

export const PRACTICE_MODE_LABEL: Record<PracticeMode, string> = {
  FLASHCARD: 'Lật thẻ',
  FILL_BLANK: 'Điền từ',
  SENTENCE_BUILD: 'Xếp câu',
};
