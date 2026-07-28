import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { API_BASE } from '../api-base';
import { ImdbSearchResult } from '../models';

/** Free-text title search against IMDb (the navbar search box). */
@Injectable({ providedIn: 'root' })
export class ImdbSearchApi {
  private readonly http = inject(HttpClient);
  private readonly base = inject(API_BASE);

  search(query: string): Observable<ImdbSearchResult[]> {
    return this.http.get<ImdbSearchResult[]>(`${this.base}imdb/search`, { params: { q: query } });
  }
}
