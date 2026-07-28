import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { API_BASE } from '../api-base';
import { Language, Theme, ViewMode } from '../models';

/** Persists the current user's own UI preferences (one PUT per preference, `/api/me/*`). */
@Injectable({ providedIn: 'root' })
export class UserPrefsApi {
  private readonly http = inject(HttpClient);
  private readonly base = inject(API_BASE);

  setTheme(theme: Theme): Observable<void> {
    return this.http.put<void>(`${this.base}me/theme`, { theme });
  }

  setShowAgeRatings(showAgeRatings: boolean): Observable<void> {
    return this.http.put<void>(`${this.base}me/show-age-ratings`, { showAgeRatings });
  }

  setLanguage(language: Language): Observable<void> {
    return this.http.put<void>(`${this.base}me/language`, { language });
  }

  setShowGermanTitle(showGermanTitle: boolean): Observable<void> {
    return this.http.put<void>(`${this.base}me/show-german-title`, { showGermanTitle });
  }

  setViewMode(viewMode: ViewMode): Observable<void> {
    return this.http.put<void>(`${this.base}me/view-mode`, { viewMode });
  }

  setTilesPerRow(tilesPerRow: number): Observable<void> {
    return this.http.put<void>(`${this.base}me/tiles-per-row`, { tilesPerRow });
  }
}
