import { Observable } from 'rxjs';

import {
  FillBlankQuestion,
  MistakeItem,
  PracticeAnswer,
  PracticeAvailability,
  PracticeResult,
  PracticeScope,
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
  /**
   * [SỬA] GET /api/practice/fill-blank?all=&deckIds=&notebook=&count=
   * Lỗi: 400 INVALID_COUNT (ngoài 1–200) · 400 PRACTICE_COUNT_EXCEEDS (nhiều hơn số câu có sẵn) · 404 DECK_NOT_FOUND
   */
  abstract fillBlank(scope: PracticeScope, count: number): Observable<FillBlankQuestion[]>;
  /** [SỬA] GET /api/practice/sentence-build?all=&deckIds=&notebook=&count= (cùng mã lỗi) */
  abstract sentenceBuild(scope: PracticeScope, count: number): Observable<SentenceBuildQuestion[]>;
  /** [THÊM] GET /api/practice/availability?all=&deckIds=&notebook= */
  abstract availability(scope: PracticeScope): Observable<PracticeAvailability>;
  /** POST /api/practice/answers. Lỗi: 404 SENSE_NOT_FOUND · 400 NOT_PRACTICABLE */
  abstract answer(answer: PracticeAnswer): Observable<PracticeResult>;
  /** GET /api/practice/stats?days= (1–30, sai → 400 INVALID_DAYS) */
  abstract stats(days: number): Observable<PracticeStats>;
  /** GET /api/mistakes?days=&limit=: nghĩa đang sai (lần gần nhất vẫn sai), sai nhiều nhất trước */
  abstract mistakes(days: number, limit: number): Observable<MistakeItem[]>;
}
