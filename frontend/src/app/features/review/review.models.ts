import { PartOfSpeech } from '../../shared/part-of-speech';

/** Một thẻ trong buổi ôn. */
export interface ReviewCard {
  cardId: number;
  word: string;
  partOfSpeech: PartOfSpeech;
  phonetic: string | null;
  definition: string;
  examples: string[];
  /** Hộp hiện tại (1..5). Thẻ mới chưa ôn lần nào cũng ở hộp 1. */
  box: number;
}

/** Kết quả chấm một thẻ, dùng cho màn tổng kết. */
export interface ReviewOutcome {
  cardId: number;
  word: string;
  remembered: boolean;
  fromBox: number;
  toBox: number;
}
