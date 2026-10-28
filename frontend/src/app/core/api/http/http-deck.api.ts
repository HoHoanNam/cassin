import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { APP_ENV } from '../../config/app-env';
import { PageResponse } from '../../models/common.models';
import { Card, CardRequest, Deck, DeckRequest } from '../../models/deck.models';
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

  listCards(deckId: number, page: number, size: number): Observable<PageResponse<Card>> {
    const params = new HttpParams().set('page', page).set('size', size);
    return this.http.get<PageResponse<Card>>(`${this.base}/decks/${deckId}/cards`, { params });
  }

  createCard(deckId: number, request: CardRequest): Observable<Card> {
    return this.http.post<Card>(`${this.base}/decks/${deckId}/cards`, request);
  }

  updateCard(cardId: number, request: CardRequest): Observable<Card> {
    return this.http.put<Card>(`${this.base}/cards/${cardId}`, request);
  }

  deleteCard(cardId: number): Observable<void> {
    return this.http.delete<void>(`${this.base}/cards/${cardId}`);
  }
}
