import { HttpClient } from '@angular/common/http';
import { ChangeDetectionStrategy, Component, effect, inject, input, signal } from '@angular/core';
import { AgeRatingStore } from '../../core/age-rating-store';
import { API_BASE } from '../../core/api-base';
import { ImdbId } from '../../core/domain';
import { AgeRating } from '../../core/models';

/**
 * A small FSK-coloured age-rating badge for a title. The rating is fetched on demand (only while the
 * user's "show age ratings" preference is on) from {@code GET /api/titles/{id}/rating}; a title
 * without a rating (404) shows nothing. FSK ratings (0/6/12/16/18) get the official colour scheme; a
 * foreign fallback certificate is shown neutral grey.
 */
@Component({
  selector: 'app-age-badge',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @if (store.showAgeRatings() && rating(); as r) {
      <span class="age-badge" [class]="badgeClass(r)" [title]="tooltip(r)">{{ r.label }}</span>
    }
  `,
})
export class AgeBadge {
  readonly imdbId = input.required<ImdbId>();

  protected readonly store = inject(AgeRatingStore);
  private readonly http = inject(HttpClient);
  private readonly base = inject(API_BASE);
  protected readonly rating = signal<AgeRating | null>(null);

  constructor() {
    // Fetch lazily the first time the badges are enabled (and never when they are off).
    effect(() => {
      if (!this.store.showAgeRatings() || this.rating() !== null) {
        return;
      }
      this.http.get<AgeRating>(`${this.base}titles/${this.imdbId()}/rating`).subscribe({
        next: (r) => this.rating.set(r),
        error: () => this.rating.set(null),
      });
    });
  }

  protected badgeClass(rating: AgeRating): string {
    return rating.system === 'FSK' ? `age-badge--fsk-${rating.label}` : 'age-badge--other';
  }

  protected tooltip(rating: AgeRating): string {
    return rating.system === 'FSK' ? `FSK ${rating.label}` : `Age rating: ${rating.label}`;
  }
}
