import { CefrLevel } from './grammar.models';

/**
 * Kiểu dữ liệu cho khu vực quản trị (/api/admin/**). Khớp 1-1 với DTO bên backend.
 */

/** Khớp AdminStatsResponse: GET /api/admin/stats */
export interface AdminStats {
  users: number;
  systemDecks: number;
  systemCards: number;
  topics: number;
  questions: number;
  quizAttempts: number;
  /** Số người có ôn thẻ trong hôm nay (bảng study_days) */
  learnersToday: number;
}

/** Khớp TopicRequest */
export interface TopicRequest {
  title: string;
  level: CefrLevel;
  explanation: string;
}

/** Khớp OptionRequest. Vị trí của đáp án = thứ tự trong mảng (0..3). */
export interface OptionRequest {
  content: string;
  correct: boolean;
}

/** Khớp QuestionRequest */
export interface QuestionRequest {
  content: string;
  explanation: string;
  options: OptionRequest[];
}

/** Khớp AdminQuestionResponse.Option. KHÁC QuizOption của học viên: có field correct. */
export interface AdminOption {
  optionId: number;
  content: string;
  correct: boolean;
  position: number;
}

/** Khớp AdminQuestionResponse */
export interface AdminQuestion {
  questionId: number;
  content: string;
  explanation: string;
  options: AdminOption[];
}

/** Giới hạn độ dài, khớp V6 (VARCHAR) và annotation @Size của backend. */
export const TOPIC_LIMITS = {
  title: 150,
} as const;

export const QUESTION_LIMITS = {
  option: 255,
  /** Question.OPTION_COUNT */
  optionCount: 4,
} as const;
