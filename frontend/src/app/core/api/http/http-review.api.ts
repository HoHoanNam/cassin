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

  deckQueue(deckId: number, newLimit: number): Observable<ReviewCard[]> {
    const params = new HttpParams().set('newLimit', newLimit);
    return this.http.get<ReviewCard[]>(`${this.base}/decks/${deckId}/review-queue`, { params });
  }

  allQueue(newLimit: number): Observable<ReviewCard[]> {
    const params = new HttpParams().set('newLimit', newLimit);
    return this.http.get<ReviewCard[]>(`${this.base}/review-queue`, { params });
  }

  review(cardId: number, remembered: boolean): Observable<ReviewResult> {
    return this.http.post<ReviewResult>(`${this.base}/cards/${cardId}/review`, { remembered });
  }

  stats(): Observable<ReviewStats> {
    return this.http.get<ReviewStats>(`${this.base}/review/stats`);
  }
}
