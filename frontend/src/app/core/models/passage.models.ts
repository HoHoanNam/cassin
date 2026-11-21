import { CefrLevel } from './grammar.models';

/**
 * [THÊM] Bài đọc hiểu. Khớp 1-1 với package com.cassin.passage.dto ở backend.
 *
 *   Admin:    DRAFT ──publish──▶ PUBLISHED ──archive──▶ ARCHIVED   (bài đã có người làm thì khóa câu hỏi)
 *   Học viên: bắt đầu lượt ─▶ xin gợi ý (từ đồng nghĩa) ─▶ nộp ─▶ xem kết quả + câu bằng chứng
 */

/** Khớp enum PassageStatus */
export type PassageStatus = 'DRAFT' | 'PUBLISHED' | 'ARCHIVED';
/** Khớp enum QuestionType */
export type QuestionType = 'MCQ' | 'TFNG' | 'BLANK';
/** Khớp enum TfngAnswer */
export type TfngAnswer = 'TRUE' | 'FALSE' | 'NOT_GIVEN';
/** Khớp enum AnswerKind */
export type AnswerKind = 'WORDS' | 'NUMBER' | 'WORDS_AND_OR_NUMBER';
/** Khớp enum RejectedReason: câu điền từ sai ĐỊNH DẠNG */
export type RejectedReason = 'OVER_WORD_LIMIT' | 'NUMBER_REQUIRED' | 'NUMBER_NOT_ALLOWED' | 'TOO_MANY_NUMBERS';

// ───────────── Học viên ─────────────

/** Khớp PassageSummary: một thẻ trong danh sách bài đọc */
export interface PassageSummary {
  id: number;
  title: string;
  level: CefrLevel;
  wordCount: number;
  readingMinutes: number;
  questionCount: number;
  /** null = chưa nộp lần nào */
  bestScore: number | null;
  publishedAt: string;
}

/** Khớp PassageView */
export interface PassageView {
  id: number;
  title: string;
  /** Hai lần xuống dòng = sang đoạn mới */
  content: string;
  level: CefrLevel;
  wordCount: number;
  readingMinutes: number;
}

/** Khớp HintView: "replacement ≈ alternative" */
export interface HintView {
  questionPhrase: string;
  passagePhrase: string;
}

/** Khớp OptionView: lựa chọn KHI ĐANG LÀM, không có field correct */
export interface OptionView {
  id: number;
  label: string;
  content: string;
}

/** Khớp QuestionView: câu hỏi khi đang làm. Không có đáp án, bằng chứng, giải thích. */
export interface QuestionView {
  id: number;
  position: number;
  type: QuestionType;
  prompt: string;
  /** Chỉ BLANK: "NO MORE THAN TWO WORDS" */
  instruction?: string;
  /** Chỉ MCQ */
  options?: OptionView[];
  revealedHints: HintView[];
  hintsRemaining: number;
}

/** Khớp AttemptResponse */
export interface AttemptResponse {
  attemptId: number;
  passage: PassageView;
  questions: QuestionView[];
  startedAt: string;
}

/** Khớp HintResponse */
export interface HintResponse {
  questionId: number;
  hint: HintView;
  hintsRemaining: number;
}

/** Khớp AnswerRequest: chỉ điền field đúng dạng câu */
export interface AnswerRequest {
  questionId: number;
  optionId?: number | null;
  tfngAnswer?: TfngAnswer | null;
  text?: string | null;
}

/** Khớp SubmitRequest */
export interface SubmitRequest {
  answers: AnswerRequest[];
}

/** Khớp OptionResult: có correct (sau khi nộp và trong trang admin) */
export interface OptionResult {
  id: number;
  label: string;
  content: string;
  correct: boolean;
}

/** Khớp QuestionResult. Field của dạng khác không có trong JSON (@JsonInclude NON_NULL). */
export interface QuestionResult {
  questionId: number;
  position: number;
  type: QuestionType;
  prompt: string;
  instruction?: string;
  options?: OptionResult[];
  selectedOptionId?: number;
  yourTfngAnswer?: TfngAnswer;
  correctTfngAnswer?: TfngAnswer;
  yourText?: string;
  acceptedAnswers?: string[];
  rejectedReason?: RejectedReason;
  correct: boolean;
  hintsUsed: number;
  hints: HintView[];
  evidence: string;
  explanation: string;
}

/** Khớp AttemptResultResponse */
export interface AttemptResult {
  attemptId: number;
  passage: PassageView;
  score: number;
  total: number;
  correctWithoutHints: number;
  startedAt: string;
  submittedAt: string;
  questions: QuestionResult[];
}

// ───────────── Admin ─────────────

/** Khớp AdminPassageSummary */
export interface AdminPassageSummary {
  id: number;
  title: string;
  level: CefrLevel;
  status: PassageStatus;
  wordCount: number;
  questionCount: number;
  updatedAt: string;
  publishedAt: string | null;
}

/** Khớp AdminQuestionResponse: hình dạng gần với QuestionRequest để sửa rồi gửi lại */
export interface AdminQuestion {
  id: number;
  position: number;
  type: QuestionType;
  prompt: string;
  evidence: string;
  explanation: string;
  hints: HintView[];
  options?: OptionResult[];
  answer?: TfngAnswer;
  answerKind?: AnswerKind;
  maxWords?: number | null;
  acceptedAnswers?: string[];
}

/** Khớp AdminPassageResponse */
export interface AdminPassage {
  id: number;
  title: string;
  content: string;
  level: CefrLevel;
  status: PassageStatus;
  wordCount: number;
  /** true = đã có người làm → không sửa / xóa được, chỉ lưu trữ */
  locked: boolean;
  createdAt: string;
  updatedAt: string;
  publishedAt: string | null;
  questions: AdminQuestion[];
}

interface QuestionRequestBase {
  prompt: string;
  evidence: string;
  explanation: string;
  hints: HintView[];
}

/**
 * Khớp sealed interface QuestionRequest: field "type" cho Jackson biết tạo record nào.
 * Union có phân biệt (discriminated union) của TypeScript: kiểm tra q.type === 'MCQ' thì TS biết q có options.
 */
export type QuestionRequest =
  | (QuestionRequestBase & { type: 'MCQ'; options: { content: string; correct: boolean }[] })
  | (QuestionRequestBase & { type: 'TFNG'; answer: TfngAnswer })
  | (QuestionRequestBase & { type: 'BLANK'; answerKind: AnswerKind; maxWords: number | null; acceptedAnswers: string[] });

/** Khớp PassageRequest: PUT thay toàn bộ nội dung + câu hỏi */
export interface PassageRequest {
  title: string;
  content: string;
  level: CefrLevel;
  questions: QuestionRequest[];
}

// ───────────── Hằng số + nhãn hiển thị ─────────────

/** Khớp các hằng số ở entity (Passage.MAX_CONTENT, ReadingQuestion.MAX_HINTS...) */
export const PASSAGE_LIMITS = {
  title: 150,
  content: 5000,
  questions: 20,
  hints: 3,
  phrase: 100,
  option: 255,
  acceptedAnswers: 5,
  answerText: 100,
} as const;

/** Chỗ trống trong đề câu điền từ (BlankAnswerRules.BLANK_MARKER) */
export const BLANK_MARKER = '____';

export const QUESTION_TYPE_LABEL: Record<QuestionType, string> = {
  MCQ: 'Trắc nghiệm A/B/C/D',
  TFNG: 'True / False / Not Given',
  BLANK: 'Điền từ',
};

export const TFNG_OPTIONS: { value: TfngAnswer; label: string }[] = [
  { value: 'TRUE', label: 'True' },
  { value: 'FALSE', label: 'False' },
  { value: 'NOT_GIVEN', label: 'Not Given' },
];

export const TFNG_LABEL: Record<TfngAnswer, string> = { TRUE: 'True', FALSE: 'False', NOT_GIVEN: 'Not Given' };

export const PASSAGE_STATUS_LABEL: Record<PassageStatus, string> = {
  DRAFT: 'Bản nháp',
  PUBLISHED: 'Đang hiện',
  ARCHIVED: 'Đã lưu trữ',
};

export const ANSWER_KIND_OPTIONS: { value: AnswerKind; label: string }[] = [
  { value: 'WORDS', label: 'Chỉ chữ' },
  { value: 'NUMBER', label: 'Một số' },
  { value: 'WORDS_AND_OR_NUMBER', label: 'Chữ và/hoặc một số' },
];

/** Giải thích vì sao câu điền từ bị chấm sai dù chưa so đáp án */
export const REJECTED_REASON_LABEL: Record<RejectedReason, string> = {
  OVER_WORD_LIMIT: 'Bạn viết quá số từ cho phép',
  NUMBER_REQUIRED: 'Đề yêu cầu viết một số',
  NUMBER_NOT_ALLOWED: 'Đề chỉ nhận chữ, không nhận số',
  TOO_MANY_NUMBERS: 'Chỉ được viết một số',
};
