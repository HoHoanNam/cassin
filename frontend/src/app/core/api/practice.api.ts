import { Observable } from 'rxjs';

import {
  FillBlankQuestion,
  MistakeItem,
  PracticeAnswer,
  PracticeResult,
  PracticeStats,
  SentenceBuildQuestion,
} from '../models/practice.models';

/**
 * [THÊM] Luyện tập theo câu ví dụ + Sổ lỗi sai (package com.cassin.practice ở backend).
 *
 * Câu hỏi KHÔNG chứa đáp án: client gửi câu trả lời, server đọc lại câu gốc để chấm.
 * Sai → nghĩa đó đến hạn ôn ngày mai; đúng → KHÔNG đổi hộp Leitner (chỉ lật thẻ mới chấm hộp).
 */
export abstract class PracticeApi {
  /** GET /api/practice/fill-blank?deckId=&count=. deckId = null: cả sổ từ. Có thể ít hơn count, hoặc rỗng. */
  abstract fillBlank(deckId: number | null, count: number): Observable<FillBlankQuestion[]>;
  /** GET /api/practice/sentence-build?deckId=&count= */
  abstract sentenceBuild(deckId: number | null, count: number): Observable<SentenceBuildQuestion[]>;
  /** POST /api/practice/answers. Lỗi: 404 SENSE_NOT_FOUND · 400 NOT_PRACTICABLE */
  abstract answer(answer: PracticeAnswer): Observable<PracticeResult>;
  /** GET /api/practice/stats?days= (1–30, sai → 400 INVALID_DAYS) */
  abstract stats(days: number): Observable<PracticeStats>;
  /** GET /api/mistakes?days=&limit=: nghĩa đang sai (lần gần nhất vẫn sai), sai nhiều nhất trước */
  abstract mistakes(days: number, limit: number): Observable<MistakeItem[]>;
}
