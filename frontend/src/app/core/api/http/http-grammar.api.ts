import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { APP_ENV } from '../../config/app-env';
import { CefrLevel, TopicDetail, TopicSummary } from '../../models/grammar.models';
import { GrammarApi } from '../grammar.api';

@Injectable()
export class HttpGrammarApi extends GrammarApi {
  private readonly http = inject(HttpClient);
  private readonly base = APP_ENV.apiBaseUrl;

  listTopics(level: CefrLevel | null): Observable<TopicSummary[]> {
    const params = level ? new HttpParams().set('level', level) : undefined;
    return this.http.get<TopicSummary[]>(`${this.base}/grammar/topics`, { params });
  }

  getTopic(topicId: number): Observable<TopicDetail> {
    return this.http.get<TopicDetail>(`${this.base}/grammar/topics/${topicId}`);
  }
}
