import { inject, Injectable, signal } from '@angular/core';
import { MatSnackBar } from '@angular/material/snack-bar';
import { WatchlistApi } from './api/watchlist-api';
import { ImdbId } from './domain';

/** How long the just-changed row stays highlighted (ms). */
const HIGHLIGHT_MS = 4000;

/**
 * Orchestrates the lightweight "mark as seen" toggle so accidental marking is noticeable and
 * reversible: it applies the change optimistically (via a caller-supplied `applyLocally`),
 * highlights the just-changed title, persists it, and offers an "Undo" snackbar.
 * On a server error it rolls the optimistic change back.
 * Shared by the overview and provider pages (which own the lists being mutated).
 */
@Injectable({ providedIn: 'root' })
export class SeenStore {
  private readonly watchlistApi = inject(WatchlistApi);
  private readonly snackBar = inject(MatSnackBar);
  private readonly _recentlyChanged = signal<ImdbId | null>(null);

  /** The title whose "seen" flag was changed most recently (drives the row highlight). */
  readonly recentlyChanged = this._recentlyChanged.asReadonly();

  /**
   * Toggle a title's seen flag.
   * `applyLocally(seen)` flips the flag in the caller's own list signal (optimistic update /
   * rollback / undo all go through it).
   */
  toggle(imdbId: ImdbId, seen: boolean, applyLocally: (seen: boolean) => void): void {
    applyLocally(seen);
    this.highlight(imdbId);

    this.watchlistApi.setSeen(imdbId, seen).subscribe({
      next: () => this.offerUndo(imdbId, seen, applyLocally),
      error: () => {
        applyLocally(!seen); // roll back the optimistic change
        this._recentlyChanged.set(null);
        this.snackBar.open('Could not save the change.', 'OK', { duration: 4000 });
      },
    });
  }

  private offerUndo(imdbId: ImdbId, seen: boolean, applyLocally: (seen: boolean) => void): void {
    const message = seen ? 'Marked as seen' : 'Marked as not seen';
    this.snackBar
      .open(message, 'Undo', { duration: HIGHLIGHT_MS })
      .onAction()
      .subscribe(() => this.toggle(imdbId, !seen, applyLocally));
  }

  private highlight(imdbId: ImdbId): void {
    this._recentlyChanged.set(imdbId);
    setTimeout(() => {
      if (this._recentlyChanged() === imdbId) {
        this._recentlyChanged.set(null);
      }
    }, HIGHLIGHT_MS);
  }
}
