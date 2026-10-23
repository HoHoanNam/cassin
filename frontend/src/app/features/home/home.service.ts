import { Injectable } from '@angular/core';
import { Observable, delay, of } from 'rxjs';

import { HOME_MOCK } from './home.mock';
import { HomeData } from './home.models';

@Injectable({ providedIn: 'root' })
export class HomeService {
  /**
   * Hiện trả dữ liệu giả, có độ trễ nhỏ để thấy được trạng thái đang tải.
   *
   * Khi có API, thay bằng:
   *   private readonly http = inject(HttpClient);
   *   getHome() { return this.http.get<HomeData>('/api/home'); }
   * (và thêm provideHttpClient() trong app.config.ts). Component không cần sửa gì.
   */
  getHome(): Observable<HomeData> {
    return of(HOME_MOCK).pipe(delay(300));
  }
}
