import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { APP_ENV } from '../../config/app-env';
import { AdminQuestion, AdminStats, QuestionRequest, TopicRequest } from '../../models/admin.models';
import { Card, CardRequest, Deck, DeckRequest } from '../../models/deck.models';
import { TopicDetail } from '../../models/grammar.models';
import { AdminApi } from '../admin.api';

@Injectable()
export class HttpAdminApi extends AdminApi {
  private readonly http = inject(HttpClient);
  private readonly base = `${APP_ENV.apiBaseUrl}/admin`;

  stats(): Observable<AdminStats> {
    return this.http.get<AdminStats>(`${this.base}/stats`);
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

  createCard(deckId: number, request: CardRequest): Observable<Card> {
    return this.http.post<Card>(`${this.base}/decks/${deckId}/cards`, request);
  }

  updateCard(cardId: number, request: CardRequest): Observable<Card> {
    return this.http.put<Card>(`${this.base}/cards/${cardId}`, request);
  }

  deleteCard(cardId: number): Observable<void> {
    return this.http.delete<void>(`${this.base}/cards/${cardId}`);
  }

  createTopic(request: TopicRequest): Observable<TopicDetail> {
    return this.http.post<TopicDetail>(`${this.base}/topics`, request);
  }

  updateTopic(topicId: number, request: TopicRequest): Observable<TopicDetail> {
    return this.http.put<TopicDetail>(`${this.base}/topics/${topicId}`, request);
  }

  deleteTopic(topicId: number): Observable<void> {
    return this.http.delete<void>(`${this.base}/topics/${topicId}`);
  }

  listQuestions(topicId: number): Observable<AdminQuestion[]> {
    return this.http.get<AdminQuestion[]>(`${this.base}/topics/${topicId}/questions`);
  }

  createQuestion(topicId: number, request: QuestionRequest): Observable<AdminQuestion> {
    return this.http.post<AdminQuestion>(`${this.base}/topics/${topicId}/questions`, request);
  }

  updateQuestion(questionId: number, request: QuestionRequest): Observable<AdminQuestion> {
    return this.http.put<AdminQuestion>(`${this.base}/questions/${questionId}`, request);
  }

  deleteQuestion(questionId: number): Observable<void> {
    return this.http.delete<void>(`${this.base}/questions/${questionId}`);
  }
}
