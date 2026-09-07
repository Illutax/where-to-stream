import { computed, effect, inject, Signal } from '@angular/core';
import { TitleMetaApi } from './api/title-meta-api';
import { ImdbId } from './domain';
import { TitleMetaResponse } from './models';
import { UserPrefsStore } from './user-prefs-store';

/**
 * Lazily fetches a title's metadata (age rating + German title) via {@link TitleMetaApi} — only
 * once per title, and only while at least one of the age-ratings or German-title preferences is
 * on, so it costs nothing when both are off.
 * Must be called from an injection context (e.g. a component constructor), mirroring functions
 * like `takeUntilDestroyed`.
 *
 * <p>"Once per title", not once per caller: {@link TitleMetaApi} hands out one shared signal per
 * id. Several components can show the same title — a table row has both a title cell and an eBay
 * column — and each of them asking separately would multiply the requests a page makes.
 */
export function injectTitleMeta(imdbId: () => ImdbId): Signal<TitleMetaResponse | null> {
  const userPrefsStore = inject(UserPrefsStore);
  const titleMetaApi = inject(TitleMetaApi);

  effect(() => {
    if (!userPrefsStore.showAgeRatings() && !userPrefsStore.showGermanTitle()) {
      return;
    }
    titleMetaApi.prefetch(imdbId());
  });

  return computed(() => titleMetaApi.metaOf(imdbId())());
}
