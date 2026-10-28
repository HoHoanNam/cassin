import { Card } from './deck.models';

/** Khớp ReviewCardResponse: một thẻ trong hàng đợi ôn. */
export interface ReviewCard {
  card: Card;
  box: number;
  isNew: boolean;
}

/** Khớp ReviewResultResponse: kết quả sau khi chấm Nhớ / Quên. */
export interface ReviewResult {
  cardId: number;
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

/**
 * Khớp ReviewStatsResponse.
 * [CẦN BE BỔ SUNG] streakDays: số ngày học liên tiếp (cần bảng nhật ký ôn).
 */
export interface ReviewStats {
  dueToday: number;
  boxes: BoxCount[];
  streakDays?: number;
}

/** Số ngày giữa hai lần ôn theo từng hộp: khớp CardProgress.intervalDays ở backend. */
export const BOX_INTERVAL_DAYS: Record<number, number> = { 1: 1, 2: 2, 3: 4, 4: 7, 5: 15 };
export const MIN_BOX = 1;
export const MAX_BOX = 5;
