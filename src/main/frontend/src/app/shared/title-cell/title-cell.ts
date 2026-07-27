import { HttpClient } from '@angular/common/http';
import { ChangeDetectionStrategy, Component, computed, effect, inject, input, signal } from '@angular/core';
import { AgeRatingStore } from '../../core/age-rating-store';
import { API_BASE } from '../../core/api-base';
import { ImdbId, imdbUrl } from '../../core/domain';
import { GermanTitleStore } from '../../core/german-title-store';
import { TitleMetaResponse } from '../../core/models';
import { AgeBadge } from '../age-badge/age-badge';
import { PosterThumb } from '../poster-thumb/poster-thumb';

/**
 * A table title cell: the poster thumbnail, the title link (in German when the user's German-title
 * preference is on and a German title exists, otherwise the original name), and the FSK age badge.
 * The per-title metadata (rating + German title) is fetched **once** from {@code /api/titles/{id}/meta}
 * — and only while at least one of those two preferences is on, so it costs nothing when both are off.
 */
@Component({
  selector: 'app-title-cell',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [PosterThumb, AgeBadge],
  template: `
    <div class="title-cell">
      <app-poster-thumb [imdbId]="imdbId()" [name]="name()" />
      <a [href]="imdbUrl(imdbId())" target="_blank" rel="noopener">{{ displayTitle() }}</a>
      @if (ageRatingStore.showAgeRatings() && meta()?.rating; as rating) {
        <app-age-badge [rating]="rating" />
      }
    </div>
  `,
})
export class TitleCell {
  readonly imdbId = input.required<ImdbId>();
  readonly name = input<string>('');

  protected readonly ageRatingStore = inject(AgeRatingStore);
  private readonly germanTitleStore = inject(GermanTitleStore);
  private readonly http = inject(HttpClient);
  private readonly base = inject(API_BASE);
  protected readonly imdbUrl = imdbUrl;
  protected readonly meta = signal<TitleMetaResponse | null>(null);

  /** The German title when the preference is on and one exists, else the original (English) name. */
  protected readonly displayTitle = computed(
    () => (this.germanTitleStore.show() && this.meta()?.germanTitle) || this.name(),
  );

  constructor() {
    // Fetch lazily the first time either preference needs it (and never when both are off).
    effect(() => {
      if ((!this.ageRatingStore.showAgeRatings() && !this.germanTitleStore.show()) || this.meta() !== null) {
        return;
      }
      this.http.get<TitleMetaResponse>(`${this.base}titles/${this.imdbId()}/meta`).subscribe({
        next: (m) => this.meta.set(m),
        error: () => this.meta.set(null),
      });
    });
  }
}
