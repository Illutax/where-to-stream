import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { API_BASE } from '../api-base';
import { ViewMode } from '../models';

/** Persists the current user's own grid-view preferences (view mode and tiles-per-row). */
@Injectable({ providedIn: 'root' })
export class GridPrefsApi {
  private readonly http = inject(HttpClient);
  private readonly base = inject(API_BASE);

  setViewMode(viewMode: ViewMode): Observable<void> {
    return this.http.put<void>(`${this.base}me/view-mode`, { viewMode });
  }

  setTilesPerRow(tilesPerRow: number): Observable<void> {
    return this.http.put<void>(`${this.base}me/tiles-per-row`, { tilesPerRow });
  }
}
