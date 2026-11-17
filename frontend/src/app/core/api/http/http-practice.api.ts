import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { APP_ENV } from '../../config/app-env';
import {
  FillBlankQuestion,
  MistakeItem,
  PracticeAnswer,
  PracticeResult,
  PracticeStats,
  SentenceBuildQuestion,
} from '../../models/practice.models';
import { PracticeApi } from '../practice.api';

/** [THÊM] */
@Injectable()
export class HttpPracticeApi extends PracticeApi {
  private readonly http = inject(HttpClient);
  private readonly base = APP_ENV.apiBaseUrl;

  fillBlank(deckId: number | null, count: number): Observable<FillBlankQuestion[]> {
    return this.http.get<FillBlankQuestion[]>(`${this.base}/practice/fill-blank`, { params: scope(deckId, count) });
  }

  sentenceBuild(deckId: number | null, count: number): Observable<SentenceBuildQuestion[]> {
    return this.http.get<SentenceBuildQuestion[]>(`${this.base}/practice/sentence-build`, { params: scope(deckId, count) });
  }

  answer(answer: PracticeAnswer): Observable<PracticeResult> {
    return this.http.post<PracticeResult>(`${this.base}/practice/answers`, answer);
  }

  stats(days: number): Observable<PracticeStats> {
    return this.http.get<PracticeStats>(`${this.base}/practice/stats`, { params: { days } });
  }

  mistakes(days: number, limit: number): Observable<MistakeItem[]> {
    return this.http.get<MistakeItem[]>(`${this.base}/mistakes`, { params: { days, limit } });
  }
}

/** deckId = null thì KHÔNG gửi tham số (backend hiểu là cả sổ từ). */
function scope(deckId: number | null, count: number): HttpParams {
  const params = new HttpParams().set('count', count);
  return deckId === null ? params : params.set('deckId', deckId);
}
