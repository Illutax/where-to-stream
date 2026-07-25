import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { API_BASE } from '../api-base';
import { Theme } from '../models';

/** Persists the current user's own theme preference (PUT /api/me/theme). */
@Injectable({ providedIn: 'root' })
export class ThemeApi {
  private readonly http = inject(HttpClient);
  private readonly base = inject(API_BASE);

  setTheme(theme: Theme): Observable<void> {
    return this.http.put<void>(`${this.base}me/theme`, { theme });
  }
}
