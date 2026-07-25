import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { API_BASE } from '../api-base';
import { ImdbId } from '../domain';
import { InvalidateResult, ManagePage, ScrapeResult } from '../models';

@Injectable({ providedIn: 'root' })
export class ManageApi {
  private readonly http = inject(HttpClient);
  private readonly base = inject(API_BASE);

  getManagePage(): Observable<ManagePage> {
    return this.http.get<ManagePage>(`${this.base}manage`);
  }

  invalidate(imdbIds: ImdbId[]): Observable<InvalidateResult> {
    return this.http.post<InvalidateResult>(`${this.base}manage/invalidate`, { imdbIds });
  }

  scrape(): Observable<ScrapeResult> {
    return this.http.post<ScrapeResult>(`${this.base}manage/scrape`, {});
  }
}
