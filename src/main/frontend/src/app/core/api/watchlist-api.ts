import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { API_BASE } from '../api-base';
import { ImdbId, ReleaseYear } from '../domain';
import { WatchlistImportResult, WatchlistStatus } from '../models';

/** The current user's own watchlist: status, CSV import (full sync) and clear. */
@Injectable({ providedIn: 'root' })
export class WatchlistApi {
  private readonly http = inject(HttpClient);
  private readonly base = inject(API_BASE);

  getStatus(): Observable<WatchlistStatus> {
    return this.http.get<WatchlistStatus>(`${this.base}watchlist`);
  }

  import(file: File): Observable<WatchlistImportResult> {
    const form = new FormData();
    form.append('file', file);
    return this.http.post<WatchlistImportResult>(`${this.base}watchlist/import`, form);
  }

  clear(): Observable<void> {
    return this.http.delete<void>(`${this.base}watchlist`);
  }

  /** Removes only the current user's watched (seen) titles, leaving the rest of the list intact. */
  clearSeen(): Observable<void> {
    return this.http.delete<void>(`${this.base}watchlist/seen`);
  }

  /** Adds a single title (found via search) to the current user's watchlist. */
  addToWatchlist(imdbId: ImdbId, name: string, year: ReleaseYear): Observable<void> {
    return this.http.post<void>(`${this.base}watchlist/${imdbId}`, { name, year });
  }

  /** Toggle a single title's "seen" flag for the current user. */
  setSeen(imdbId: ImdbId, seen: boolean): Observable<void> {
    return this.http.put<void>(`${this.base}watchlist/${imdbId}/seen`, { seen });
  }
}
