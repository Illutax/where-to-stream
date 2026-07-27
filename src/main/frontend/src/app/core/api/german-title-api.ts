import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { API_BASE } from '../api-base';

/** Persists the current user's own German-title preference (PUT /api/me/show-german-title). */
@Injectable({ providedIn: 'root' })
export class GermanTitleApi {
  private readonly http = inject(HttpClient);
  private readonly base = inject(API_BASE);

  setShowGermanTitle(showGermanTitle: boolean): Observable<void> {
    return this.http.put<void>(`${this.base}me/show-german-title`, { showGermanTitle });
  }
}
