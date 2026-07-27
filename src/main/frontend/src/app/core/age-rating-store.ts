import { inject, Injectable, signal } from '@angular/core';
import { AgeRatingApi } from './api/age-rating-api';

/**
 * Holds the user's "show FSK age-rating badges" preference. Loaded from the account on app start
 * (via {@code /api/me}) and persisted on change. The badges read {@link showAgeRatings} to decide
 * whether to render and fetch.
 */
@Injectable({ providedIn: 'root' })
export class AgeRatingStore {
  private readonly ageRatingApi = inject(AgeRatingApi);
  private readonly _showAgeRatings = signal(true);

  readonly showAgeRatings = this._showAgeRatings.asReadonly();

  /** Adopt the preference loaded from the server without persisting it back. */
  init(showAgeRatings: boolean): void {
    this._showAgeRatings.set(showAgeRatings);
  }

  /** User-initiated change: apply immediately and persist it for the account. */
  set(showAgeRatings: boolean): void {
    this._showAgeRatings.set(showAgeRatings);
    this.ageRatingApi.setShowAgeRatings(showAgeRatings).subscribe({ error: () => undefined });
  }
}
