import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { APP_ENV } from '../../config/app-env';
import { ReviewCard, ReviewResult, ReviewStats } from '../../models/review.models';
import { ReviewApi } from '../review.api';

@Injectable()
export class HttpReviewApi extends ReviewApi {
  private readonly http = inject(HttpClient);
  private readonly base = APP_ENV.apiBaseUrl;

  queue(deckId: number | null, newLimit: number): Observable<ReviewCard[]> {
    let params = new HttpParams().set('newLimit', newLimit);
    if (deckId !== null) {
      params = params.set('deckId', deckId);
    }
    return this.http.get<ReviewCard[]>(`${this.base}/review-queue`, { params });
  }

  review(senseId: number, remembered: boolean, durationMs: number): Observable<ReviewResult> {
    return this.http.post<ReviewResult>(`${this.base}/senses/${senseId}/review`, { remembered, durationMs });
  }

  stats(): Observable<ReviewStats> {
    return this.http.get<ReviewStats>(`${this.base}/review/stats`);
  }
}
