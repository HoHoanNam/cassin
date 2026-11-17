import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { APP_ENV } from '../../config/app-env';
import { PageResponse } from '../../models/common.models';
import { AdminReport, CreateReportRequest, Report, ReportStatus, ReviewReportRequest } from '../../models/report.models';
import { ReportApi } from '../report.api';

/** [THÊM] */
@Injectable()
export class HttpReportApi extends ReportApi {
  private readonly http = inject(HttpClient);
  private readonly base = APP_ENV.apiBaseUrl;

  create(request: CreateReportRequest): Observable<Report> {
    return this.http.post<Report>(`${this.base}/reports`, request);
  }

  listMine(page: number, size: number): Observable<PageResponse<Report>> {
    return this.http.get<PageResponse<Report>>(`${this.base}/reports`, { params: { page, size } });
  }

  withdraw(reportId: number): Observable<void> {
    return this.http.delete<void>(`${this.base}/reports/${reportId}`);
  }

  adminList(status: ReportStatus | null, page: number, size: number): Observable<PageResponse<AdminReport>> {
    let params = new HttpParams().set('page', page).set('size', size);
    if (status) {
      params = params.set('status', status);
    }
    return this.http.get<PageResponse<AdminReport>>(`${this.base}/admin/reports`, { params });
  }

  openCount(): Observable<number> {
    return this.http.get<number>(`${this.base}/admin/reports/open-count`);
  }

  review(reportId: number, request: ReviewReportRequest): Observable<AdminReport> {
    return this.http.patch<AdminReport>(`${this.base}/admin/reports/${reportId}`, request);
  }
}
