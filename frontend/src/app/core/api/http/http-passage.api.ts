import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable, map } from 'rxjs';

import { APP_ENV } from '../../config/app-env';
import { PageResponse } from '../../models/common.models';
import { CefrLevel } from '../../models/grammar.models';
import {
  AdminPassage,
  AdminPassageSummary,
  AttemptResponse,
  AttemptResult,
  HintResponse,
  PassageRequest,
  PassageStatus,
  PassageSummary,
  SubmitRequest,
} from '../../models/passage.models';
import { PassageApi, StartedAttempt } from '../passage.api';

/** [THÊM] */
@Injectable()
export class HttpPassageApi extends PassageApi {
  private readonly http = inject(HttpClient);
  private readonly base = APP_ENV.apiBaseUrl;

  list(level: CefrLevel | null, page: number, size: number): Observable<PageResponse<PassageSummary>> {
    let params = new HttpParams().set('page', page).set('size', size);
    if (level) {
      params = params.set('level', level);
    }
    return this.http.get<PageResponse<PassageSummary>>(`${this.base}/passages`, { params });
  }

  /** observe: 'response' để đọc được mã 201 / 200 (body giống nhau, chỉ khác mã). */
  start(passageId: number): Observable<StartedAttempt> {
    return this.http
      .post<AttemptResponse>(`${this.base}/passages/${passageId}/attempts`, null, { observe: 'response' })
      .pipe(map((response) => ({ attempt: response.body!, created: response.status === 201 })));
  }

  revealHint(attemptId: number, questionId: number): Observable<HintResponse> {
    return this.http.post<HintResponse>(`${this.base}/reading-attempts/${attemptId}/questions/${questionId}/hint`, null);
  }

  submit(attemptId: number, request: SubmitRequest): Observable<AttemptResult> {
    return this.http.post<AttemptResult>(`${this.base}/reading-attempts/${attemptId}/submit`, request);
  }

  result(attemptId: number): Observable<AttemptResult> {
    return this.http.get<AttemptResult>(`${this.base}/reading-attempts/${attemptId}/result`);
  }

  adminList(status: PassageStatus | null, page: number, size: number): Observable<PageResponse<AdminPassageSummary>> {
    let params = new HttpParams().set('page', page).set('size', size);
    if (status) {
      params = params.set('status', status);
    }
    return this.http.get<PageResponse<AdminPassageSummary>>(`${this.base}/admin/passages`, { params });
  }

  adminGet(passageId: number): Observable<AdminPassage> {
    return this.http.get<AdminPassage>(`${this.base}/admin/passages/${passageId}`);
  }

  adminCreate(request: PassageRequest): Observable<AdminPassage> {
    return this.http.post<AdminPassage>(`${this.base}/admin/passages`, request);
  }

  adminUpdate(passageId: number, request: PassageRequest): Observable<AdminPassage> {
    return this.http.put<AdminPassage>(`${this.base}/admin/passages/${passageId}`, request);
  }

  adminPublish(passageId: number): Observable<AdminPassage> {
    return this.http.patch<AdminPassage>(`${this.base}/admin/passages/${passageId}/publish`, null);
  }

  adminArchive(passageId: number): Observable<AdminPassage> {
    return this.http.patch<AdminPassage>(`${this.base}/admin/passages/${passageId}/archive`, null);
  }

  adminDelete(passageId: number): Observable<void> {
    return this.http.delete<void>(`${this.base}/admin/passages/${passageId}`);
  }
}
