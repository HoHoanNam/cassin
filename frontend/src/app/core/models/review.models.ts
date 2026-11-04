import { PartOfSpeech } from './deck.models';

/** Khớp ReviewCardResponse.OtherSense: nghĩa KHÁC của cùng từ (mặt sau thẻ hiện mờ). */
export interface OtherSense {
  partOfSpeech: PartOfSpeech;
  definition: string;
}

/**
 * Khớp ReviewCardResponse: một thẻ trong hàng đợi ôn = MỘT nghĩa của một từ.
 * [V10] examples đã bỏ dấu [ ] (chỉ để đọc).
 */
export interface ReviewCard {
  senseId: number;
  wordId: number;
  term: string;
  phonetic: string | null;
  partOfSpeech: PartOfSpeech;
  definition: string;
  examples: string[];
  otherSenses: OtherSense[];
  box: number;
  isNew: boolean;
}

/** Khớp ReviewResultResponse: kết quả sau khi chấm Nhớ / Quên. */
export interface ReviewResult {
  senseId: number;
  box: number;
  /** yyyy-MM-dd (LocalDate của Java) */
  dueDate: string;
  reviewCount: number;
  lapseCount: number;
}

/** Khớp BoxCount */
export interface BoxCount {
  box: number;
  total: number;
}

/** Khớp ReviewStatsResponse. */
export interface ReviewStats {
  dueToday: number;
  boxes: BoxCount[];
  streakDays: number;
}

/** Số ngày giữa hai lần ôn theo từng hộp: khớp CardProgress.intervalDays ở backend. */
export const BOX_INTERVAL_DAYS: Record<number, number> = { 1: 1, 2: 2, 3: 4, 4: 7, 5: 15 };
export const MIN_BOX = 1;
export const MAX_BOX = 5;
