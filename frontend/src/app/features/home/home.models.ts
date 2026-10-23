/**
 * Kiểu dữ liệu của trang chủ. Đặt tên và kiểu giống DTO backend sẽ trả về,
 * để khi có API thật chỉ cần đổi HomeService, không phải sửa component.
 */

export type DeckType = 'SYSTEM_DECK' | 'USER_DECK';
export type CefrLevel = 'A1' | 'A2' | 'B1' | 'B2' | 'C1' | 'C2';

export interface ReviewSummary {
  /** Số thẻ đến hạn hôm nay (cả thẻ mới chưa ôn lần nào). */
  dueToday: number;
  /** Số thẻ đang nằm ở từng hộp Leitner, phần tử 0 là hộp 1. */
  boxCounts: [number, number, number, number, number];
  /** Số ngày học liên tiếp. */
  streakDays: number;
}

export interface DeckSummary {
  id: number;
  name: string;
  type: DeckType;
  cardCount: number;
  dueCount: number;
}

export interface GrammarTopicSummary {
  id: number;
  title: string;
  level: CefrLevel;
  questionCount: number;
}

export interface QuizAttemptSummary {
  id: number;
  topicTitle: string;
  score: number;
  total: number;
  /** ISO-8601, ví dụ "2026-10-22T14:30:00Z" (kiểu Instant của Java). */
  submittedAt: string;
}

export interface HomeData {
  displayName: string;
  review: ReviewSummary;
  decks: DeckSummary[];
  grammarTopics: GrammarTopicSummary[];
  recentQuizzes: QuizAttemptSummary[];
}
