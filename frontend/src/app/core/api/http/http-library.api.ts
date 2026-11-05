import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { APP_ENV } from '../../config/app-env';
import { PageResponse } from '../../models/common.models';
import { LibraryWord } from '../../models/deck.models';
import { LibraryApi } from '../library.api';

@Injectable()
export class HttpLibraryApi extends LibraryApi {
  private readonly http = inject(HttpClient);
  private readonly base = `${APP_ENV.apiBaseUrl}/library`;

  search(q: string, onlyNew: boolean, page: number, size: number): Observable<PageResponse<LibraryWord>> {
    const params = new HttpParams().set('q', q).set('onlyNew', onlyNew).set('page', page).set('size', size);
    return this.http.get<PageResponse<LibraryWord>>(this.base, { params });
  }
}
