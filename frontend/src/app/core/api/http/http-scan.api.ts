import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { APP_ENV } from '../../config/app-env';
import { ScanRequest, ScanResult } from '../../models/scan.models';
import { ScanApi } from '../scan.api';

@Injectable()
export class HttpScanApi extends ScanApi {
  private readonly http = inject(HttpClient);

  scan(text: string): Observable<ScanResult> {
    const body: ScanRequest = { text };
    return this.http.post<ScanResult>(`${APP_ENV.apiBaseUrl}/scan`, body);
  }
}
