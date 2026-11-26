import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { APP_ENV } from '../../config/app-env';
import {
  FillBlankQuestion,
  MistakeItem,
  PracticeAnswer,
  PracticeAvailability,
  PracticeResult,
  PracticeScope,
  PracticeStats,
  SentenceBuildQuestion,
} from '../../models/practice.models';
import { PracticeApi } from '../practice.api';

/** [THÊM] */
@Injectable()
export class HttpPracticeApi extends PracticeApi {
  private readonly http = inject(HttpClient);
  private readonly base = APP_ENV.apiBaseUrl;

  fillBlank(scope: PracticeScope, count: number): Observable<FillBlankQuestion[]> {
    return this.http.get<FillBlankQuestion[]>(`${this.base}/practice/fill-blank`, {
      params: scopeParams(scope).set('count', count),
    });
  }

  sentenceBuild(scope: PracticeScope, count: number): Observable<SentenceBuildQuestion[]> {
    return this.http.get<SentenceBuildQuestion[]>(`${this.base}/practice/sentence-build`, {
      params: scopeParams(scope).set('count', count),
    });
  }

  availability(scope: PracticeScope): Observable<PracticeAvailability> {
    return this.http.get<PracticeAvailability>(`${this.base}/practice/availability`, { params: scopeParams(scope) });
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

/** [SỬA] Chỉ gửi tham số đang bật: ?all=true | ?deckIds=3,7&notebook=true (backend: không gì = sổ từ). */
function scopeParams(scope: PracticeScope): HttpParams {
  let params = new HttpParams();
  if (scope.all) {
    return params.set('all', true);
  }
  if (scope.deckIds.length > 0) {
    params = params.set('deckIds', scope.deckIds.join(','));
  }
  return scope.notebook ? params.set('notebook', true) : params;
}
