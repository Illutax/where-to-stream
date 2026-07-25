import { inject, Injectable, signal } from '@angular/core';
import { WatchlistApi } from './api/watchlist-api';

/**
 * Holds the current user's watchlist size for the navbar — the Angular equivalent of the server's
 * CommonAttributeService (which injected "watchlistCount" into every Thymeleaf model). Loaded once
 * on app start and refreshed after an import or clear.
 */
@Injectable({ providedIn: 'root' })
export class WatchlistStore {
  private readonly watchlistApi = inject(WatchlistApi);
  private readonly _count = signal<number | null>(null);

  /** The number of titles on the watchlist, or null until loaded. */
  readonly count = this._count.asReadonly();

  load(): void {
    this.watchlistApi.getStatus().subscribe({
      next: (status) => this._count.set(status.count),
    });
  }

  set(count: number): void {
    this._count.set(count);
  }
}
