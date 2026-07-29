import { effect, inject, Signal, signal } from '@angular/core';
import { TitleMetaApi } from './api/title-meta-api';
import { ImdbId } from './domain';
import { TitleMetaResponse } from './models';
import { UserPrefsStore } from './user-prefs-store';

/**
 * Lazily fetches a title's metadata (age rating + German title) via {@link TitleMetaApi} — only
 * once, and only while at least one of the age-ratings or German-title preferences is on, so it
 * costs nothing when both are off.
 * Must be called from an injection context (e.g. a component constructor), mirroring functions
 * like `takeUntilDestroyed`.
 */
export function injectTitleMeta(imdbId: () => ImdbId): Signal<TitleMetaResponse | null> {
  const userPrefsStore = inject(UserPrefsStore);
  const titleMetaApi = inject(TitleMetaApi);
  const meta = signal<TitleMetaResponse | null>(null);

  effect(() => {
    if ((!userPrefsStore.showAgeRatings() && !userPrefsStore.showGermanTitle()) || meta() !== null) {
      return;
    }
    titleMetaApi.get(imdbId()).subscribe({
      next: (m) => meta.set(m),
      error: () => meta.set(null),
    });
  });

  return meta.asReadonly();
}
