import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { APP_ENV } from '../../config/app-env';
import { PageResponse } from '../../models/common.models';
import { QuizHistoryItem, QuizResult, QuizStart, SubmitQuizRequest } from '../../models/quiz.models';
import { QuizApi } from '../quiz.api';

@Injectable()
export class HttpQuizApi extends QuizApi {
  private readonly http = inject(HttpClient);
  private readonly base = APP_ENV.apiBaseUrl;

  start(topicId: number): Observable<QuizStart> {
    return this.http.post<QuizStart>(`${this.base}/grammar/topics/${topicId}/quiz-attempts`, {});
  }

  submit(attemptId: number, request: SubmitQuizRequest): Observable<QuizResult> {
    return this.http.post<QuizResult>(`${this.base}/quiz-attempts/${attemptId}/submit`, request);
  }

  result(attemptId: number): Observable<QuizResult> {
    return this.http.get<QuizResult>(`${this.base}/quiz-attempts/${attemptId}/result`);
  }

  history(page: number, size: number): Observable<PageResponse<QuizHistoryItem>> {
    const params = new HttpParams().set('page', page).set('size', size);
    return this.http.get<PageResponse<QuizHistoryItem>>(`${this.base}/quiz-attempts`, { params });
  }
}
