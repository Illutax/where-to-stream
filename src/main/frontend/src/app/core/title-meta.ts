import { effect, inject, Signal, signal } from '@angular/core';
import { AgeRatingStore } from './age-rating-store';
import { TitleMetaApi } from './api/title-meta-api';
import { ImdbId } from './domain';
import { GermanTitleStore } from './german-title-store';
import { TitleMetaResponse } from './models';

/**
 * Lazily fetches a title's metadata (age rating + German title) via {@link TitleMetaApi} — only
 * once, and only while at least one of the age-ratings or German-title preferences is on, so it
 * costs nothing when both are off. Must be called from an injection context (e.g. a component
 * constructor), mirroring functions like `takeUntilDestroyed`.
 */
export function injectTitleMeta(imdbId: () => ImdbId): Signal<TitleMetaResponse | null> {
  const ageRatingStore = inject(AgeRatingStore);
  const germanTitleStore = inject(GermanTitleStore);
  const titleMetaApi = inject(TitleMetaApi);
  const meta = signal<TitleMetaResponse | null>(null);

  effect(() => {
    if ((!ageRatingStore.showAgeRatings() && !germanTitleStore.show()) || meta() !== null) {
      return;
    }
    titleMetaApi.get(imdbId()).subscribe({
      next: (m) => meta.set(m),
      error: () => meta.set(null),
    });
  });

  return meta.asReadonly();
}
