import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { APP_ENV } from '../../config/app-env';
import { PageResponse } from '../../models/common.models';
import { Deck, DeckRequest, WordSummary } from '../../models/deck.models';
import { ImportResult } from '../../models/import.models';
import { DeckApi } from '../deck.api';

@Injectable()
export class HttpDeckApi extends DeckApi {
  private readonly http = inject(HttpClient);
  private readonly base = APP_ENV.apiBaseUrl;

  listDecks(): Observable<Deck[]> {
    return this.http.get<Deck[]>(`${this.base}/decks`);
  }

  getDeck(deckId: number): Observable<Deck> {
    return this.http.get<Deck>(`${this.base}/decks/${deckId}`);
  }

  createDeck(request: DeckRequest): Observable<Deck> {
    return this.http.post<Deck>(`${this.base}/decks`, request);
  }

  updateDeck(deckId: number, request: DeckRequest): Observable<Deck> {
    return this.http.put<Deck>(`${this.base}/decks/${deckId}`, request);
  }

  deleteDeck(deckId: number): Observable<void> {
    return this.http.delete<void>(`${this.base}/decks/${deckId}`);
  }

  listWords(deckId: number, q: string, page: number, size: number): Observable<PageResponse<WordSummary>> {
    const params = new HttpParams().set('q', q).set('page', page).set('size', size);
    return this.http.get<PageResponse<WordSummary>>(`${this.base}/decks/${deckId}/words`, { params });
  }

  addWord(deckId: number, wordId: number): Observable<void> {
    return this.http.post<void>(`${this.base}/decks/${deckId}/words/${wordId}`, null);
  }

  removeWord(deckId: number, wordId: number): Observable<void> {
    return this.http.delete<void>(`${this.base}/decks/${deckId}/words/${wordId}`);
  }

  importWords(deckId: number, file: File, dryRun: boolean): Observable<ImportResult> {
    // FormData → trình duyệt tự đặt Content-Type: multipart/form-data kèm boundary (KHÔNG tự set header)
    const form = new FormData();
    form.append('file', file);   // tên "file" khớp @RequestParam("file") ở DeckController
    const params = new HttpParams().set('dryRun', dryRun);
    return this.http.post<ImportResult>(`${this.base}/decks/${deckId}/import`, form, { params });
  }
}
