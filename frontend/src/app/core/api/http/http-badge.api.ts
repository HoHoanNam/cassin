import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { APP_ENV } from '../../config/app-env';
import { Badge, EarnedBadge, MarkSeenRequest, SeenBadge } from '../../models/badge.models';
import { BadgeApi } from '../badge.api';

/** [THÊM] Gọi BadgeController ở backend. */
@Injectable()
export class HttpBadgeApi extends BadgeApi {
  private readonly http = inject(HttpClient);
  private readonly base = APP_ENV.apiBaseUrl;

  list(): Observable<Badge[]> {
    return this.http.get<Badge[]>(`${this.base}/badges`);
  }

  unseen(): Observable<EarnedBadge[]> {
    return this.http.get<EarnedBadge[]>(`${this.base}/badges/unseen`);
  }

  markSeen(items: SeenBadge[]): Observable<void> {
    const body: MarkSeenRequest = { items };
    return this.http.post<void>(`${this.base}/badges/seen`, body);
  }
}
