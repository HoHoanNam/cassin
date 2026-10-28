/** Khớp OptionResponse: đáp án lúc LÀM BÀI, không có cờ "correct". */
export interface QuizOption {
  id: number;
  content: string;
}

/** Khớp QuizQuestionResponse */
export interface QuizQuestion {
  questionId: number;
  position: number;
  content: string;
  options: QuizOption[];
}

/** Khớp QuizStartResponse */
export interface QuizStart {
  attemptId: number;
  topicId: number;
  topicTitle: string;
  total: number;
  questions: QuizQuestion[];
}

/** Khớp SubmitQuizRequest */
export interface SubmitQuizRequest {
  answers: { questionId: number; optionId: number | null }[];
}

/** Khớp AnswerResult: một câu trong KẾT QUẢ, lúc này mới có đáp án đúng. */
export interface AnswerResult {
  questionId: number;
  position: number;
  content: string;
  options: QuizOption[];
  selectedOptionId: number | null;
  correctOptionId: number;
  correct: boolean;
  explanation: string;
}

/** Khớp QuizResultResponse */
export interface QuizResult {
  attemptId: number;
  topicId: number;
  topicTitle: string;
  score: number;
  total: number;
  submittedAt: string;
  answers: AnswerResult[];
}

/** Khớp QuizHistoryItem */
export interface QuizHistoryItem {
  attemptId: number;
  topicId: number;
  topicTitle: string;
  score: number | null;
  total: number;
  startedAt: string;
  submittedAt: string | null;
}
