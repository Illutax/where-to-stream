import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { API_BASE } from '../api-base';
import { ImdbId } from '../domain';
import { TitleMetaResponse } from '../models';

/** Per-title metadata (age rating + German title), cached server-side per title. */
@Injectable({ providedIn: 'root' })
export class TitleMetaApi {
  private readonly http = inject(HttpClient);
  private readonly base = inject(API_BASE);

  get(imdbId: ImdbId): Observable<TitleMetaResponse> {
    return this.http.get<TitleMetaResponse>(`${this.base}titles/${imdbId}/meta`);
  }
}
