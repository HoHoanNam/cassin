import { Observable } from 'rxjs';

import { PageResponse } from '../models/common.models';
import { QuizHistoryItem, QuizResult, QuizStart, SubmitQuizRequest } from '../models/quiz.models';

export abstract class QuizApi {
  /** POST /api/grammar/topics/{topicId}/quiz-attempts → 201 */
  abstract start(topicId: number): Observable<QuizStart>;
  /** POST /api/quiz-attempts/{attemptId}/submit */
  abstract submit(attemptId: number, request: SubmitQuizRequest): Observable<QuizResult>;
  /** GET /api/quiz-attempts/{attemptId}/result */
  abstract result(attemptId: number): Observable<QuizResult>;
  /** GET /api/quiz-attempts?page=&size= */
  abstract history(page: number, size: number): Observable<PageResponse<QuizHistoryItem>>;
}
