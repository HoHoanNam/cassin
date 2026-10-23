import { CefrLevel } from '../home/home.models';

/** Đáp án như giao diện nhận được: KHÔNG có cờ đúng/sai (chấm hoàn toàn ở server). */
export interface QuizOption {
  id: number;
  content: string;
}

export interface QuizQuestion {
  id: number;
  content: string;
  options: QuizOption[];
}

/** Đề nhận được khi bắt đầu một lượt (POST /api/quizzes). */
export interface QuizSession {
  attemptId: number;
  topicTitle: string;
  level: CefrLevel;
  questions: QuizQuestion[];
}

/** Kết quả từng câu, chỉ có SAU khi nộp bài. */
export interface QuizAnswerResult {
  questionId: number;
  selectedOptionId: number | null;
  correctOptionId: number;
  correct: boolean;
  explanation: string;
}

export interface QuizResult {
  score: number;
  total: number;
  answers: QuizAnswerResult[];
}
