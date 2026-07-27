import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { API_BASE } from '../api-base';

/** Persists the current user's own age-rating-badge preference (PUT /api/me/show-age-ratings). */
@Injectable({ providedIn: 'root' })
export class AgeRatingApi {
  private readonly http = inject(HttpClient);
  private readonly base = inject(API_BASE);

  setShowAgeRatings(showAgeRatings: boolean): Observable<void> {
    return this.http.put<void>(`${this.base}me/show-age-ratings`, { showAgeRatings });
  }
}
