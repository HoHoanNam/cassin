import { Observable } from 'rxjs';

import { PageResponse } from '../models/common.models';
import { CefrLevel } from '../models/grammar.models';
import {
  AdminPassage,
  AdminPassageSummary,
  AttemptResponse,
  AttemptResult,
  HintResponse,
  PassageRequest,
  PassageStatus,
  PassageSummary,
  SubmitRequest,
} from '../models/passage.models';

/** Kết quả "Làm bài": created = true khi server tạo lượt mới (201), false khi trả lại lượt đang làm dở (200). */
export interface StartedAttempt {
  attempt: AttemptResponse;
  created: boolean;
}

/**
 * [THÊM] Bài đọc hiểu. Gom cả hai phía như module com.cassin.passage ở backend.
 */
export abstract class PassageApi {
  // ---------- Học viên ----------
  /** GET /api/passages?level=&page=&size= (chỉ bài PUBLISHED, mới nhất trước) */
  abstract list(level: CefrLevel | null, page: number, size: number): Observable<PageResponse<PassageSummary>>;
  /** POST /api/passages/{id}/attempts → 201 lượt mới · 200 lượt đang dở · 404 PASSAGE_NOT_FOUND */
  abstract start(passageId: number): Observable<StartedAttempt>;
  /** POST /api/reading-attempts/{id}/questions/{qid}/hint · 409 NO_MORE_HINTS · 409 ATTEMPT_SUBMITTED */
  abstract revealHint(attemptId: number, questionId: number): Observable<HintResponse>;
  /** POST /api/reading-attempts/{id}/submit · 400 INVALID_ANSWER · 409 ATTEMPT_SUBMITTED */
  abstract submit(attemptId: number, request: SubmitRequest): Observable<AttemptResult>;
  /** GET /api/reading-attempts/{id}/result · 409 ATTEMPT_NOT_SUBMITTED · 404 ATTEMPT_NOT_FOUND */
  abstract result(attemptId: number): Observable<AttemptResult>;

  // ---------- Quản trị: /api/admin/passages ----------
  /** status = null: mọi trạng thái */
  abstract adminList(status: PassageStatus | null, page: number, size: number): Observable<PageResponse<AdminPassageSummary>>;
  abstract adminGet(passageId: number): Observable<AdminPassage>;
  /** → 201 bản nháp · 400 INVALID_PASSAGE ("Câu 3: ...") */
  abstract adminCreate(request: PassageRequest): Observable<AdminPassage>;
  /** Thay toàn bộ · 409 PASSAGE_LOCKED (đã có người làm) · 409 PASSAGE_STATUS (đã lưu trữ) */
  abstract adminUpdate(passageId: number, request: PassageRequest): Observable<AdminPassage>;
  abstract adminPublish(passageId: number): Observable<AdminPassage>;
  abstract adminArchive(passageId: number): Observable<AdminPassage>;
  /** → 204 · 409 PASSAGE_LOCKED */
  abstract adminDelete(passageId: number): Observable<void>;
}
