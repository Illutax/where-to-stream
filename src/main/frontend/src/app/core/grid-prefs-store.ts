import { inject, Injectable, signal } from '@angular/core';
import { GridPrefsApi } from './api/grid-prefs-api';
import { ViewMode } from './models';

/**
 * Holds the user's library-layout preferences: list vs. poster grid, and tiles per row (2-6) in
 * the grid. Loaded from the account on app start (via {@code /api/me}) and persisted on change.
 */
@Injectable({ providedIn: 'root' })
export class GridPrefsStore {
  private readonly gridPrefsApi = inject(GridPrefsApi);
  private readonly _viewMode = signal<ViewMode>('GRID');
  private readonly _tilesPerRow = signal(6);

  readonly viewMode = this._viewMode.asReadonly();
  readonly tilesPerRow = this._tilesPerRow.asReadonly();

  /** Adopt the preferences loaded from the server without persisting them back. */
  init(viewMode: ViewMode, tilesPerRow: number): void {
    this._viewMode.set(viewMode);
    this._tilesPerRow.set(tilesPerRow);
  }

  /** User-initiated change: apply immediately and persist it for the account. */
  setViewMode(viewMode: ViewMode): void {
    this._viewMode.set(viewMode);
    this.gridPrefsApi.setViewMode(viewMode).subscribe({ error: () => undefined });
  }

  /** User-initiated change: apply immediately and persist it for the account. */
  setTilesPerRow(tilesPerRow: number): void {
    this._tilesPerRow.set(tilesPerRow);
    this.gridPrefsApi.setTilesPerRow(tilesPerRow).subscribe({ error: () => undefined });
  }
}
