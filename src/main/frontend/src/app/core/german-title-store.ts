import { inject, Injectable, signal } from '@angular/core';
import { GermanTitleApi } from './api/german-title-api';

/**
 * Holds the user's "show German film titles" preference. Loaded from the account on app start (via
 * {@code /api/me}) and persisted on change. The title cells read {@link show} to decide whether to
 * display the German title (when available) instead of the English one.
 */
@Injectable({ providedIn: 'root' })
export class GermanTitleStore {
  private readonly germanTitleApi = inject(GermanTitleApi);
  private readonly _show = signal(false);

  readonly show = this._show.asReadonly();

  /** Adopt the preference loaded from the server without persisting it back. */
  init(show: boolean): void {
    this._show.set(show);
  }

  /** User-initiated change: apply immediately and persist it for the account. */
  set(show: boolean): void {
    this._show.set(show);
    this.germanTitleApi.setShowGermanTitle(show).subscribe({ error: () => undefined });
  }
}
