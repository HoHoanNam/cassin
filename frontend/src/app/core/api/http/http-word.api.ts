import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { APP_ENV } from '../../config/app-env';
import { PageResponse } from '../../models/common.models';
import { Word, WordRequest, WordSummary } from '../../models/deck.models';
import { WordApi } from '../word.api';

@Injectable()
export class HttpWordApi extends WordApi {
  private readonly http = inject(HttpClient);
  private readonly base = `${APP_ENV.apiBaseUrl}/words`;

  listWords(
    q: string,
    page: number,
    size: number,
    deckId?: number,
  ): Observable<PageResponse<WordSummary>> {
    let params = new HttpParams().set('q', q).set('page', page).set('size', size);
    if (deckId !== undefined) {
      params = params.set('deckId', deckId);
    }
    return this.http.get<PageResponse<WordSummary>>(this.base, { params });
  }

  getWord(wordId: number): Observable<Word> {
    return this.http.get<Word>(`${this.base}/${wordId}`);
  }

  createWord(request: WordRequest): Observable<Word> {
    return this.http.post<Word>(this.base, request);
  }

  updateWord(wordId: number, request: WordRequest): Observable<Word> {
    return this.http.put<Word>(`${this.base}/${wordId}`, request);
  }

  deleteWord(wordId: number): Observable<void> {
    return this.http.delete<void>(`${this.base}/${wordId}`);
  }
}
