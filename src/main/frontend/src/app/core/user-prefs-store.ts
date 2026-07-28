import { DOCUMENT } from '@angular/common';
import { computed, inject, Injectable, signal } from '@angular/core';
import { UserPrefsApi } from './api/user-prefs-api';
import { Language, Theme, ViewMode } from './models';

export interface UserPrefs {
  theme: Theme;
  showAgeRatings: boolean;
  language: Language;
  showGermanTitle: boolean;
  viewMode: ViewMode;
  tilesPerRow: number;
}

const DEFAULTS: UserPrefs = {
  theme: 'SYSTEM',
  showAgeRatings: true,
  language: 'EN',
  showGermanTitle: false,
  viewMode: 'GRID',
  tilesPerRow: 6,
};

/**
 * Holds and persists every one of the user's own UI preferences — theme, language, age-rating
 * badges, German titles, and the library view mode + tiles-per-row — behind one store instead of
 * one store/api pair per preference (they're all populated from the same `/api/me` response and
 * were previously six near-identical `init()`/`set()` pairs). Loaded from the account on app start
 * (`init()`, no persist) and persisted individually on change (each `setX` fires its own PUT, so
 * changing one preference never bundles an unrelated one into the same request).
 */
@Injectable({ providedIn: 'root' })
export class UserPrefsStore {
  private readonly api = inject(UserPrefsApi);
  private readonly document = inject(DOCUMENT);
  private readonly _prefs = signal<UserPrefs>(DEFAULTS);

  readonly theme = computed(() => this._prefs().theme);
  readonly showAgeRatings = computed(() => this._prefs().showAgeRatings);
  readonly language = computed(() => this._prefs().language);
  readonly showGermanTitle = computed(() => this._prefs().showGermanTitle);
  readonly viewMode = computed(() => this._prefs().viewMode);
  readonly tilesPerRow = computed(() => this._prefs().tilesPerRow);

  /** Adopt (all or some of) the preferences loaded from the server without persisting them back. */
  init(prefs: Partial<UserPrefs>): void {
    this._prefs.update((current) => ({ ...current, ...prefs }));
    this.applyTheme(this._prefs().theme);
  }

  setTheme(theme: Theme): void {
    this._prefs.update((p) => ({ ...p, theme }));
    this.applyTheme(theme);
    this.api.setTheme(theme).subscribe({ error: () => undefined });
  }

  setShowAgeRatings(showAgeRatings: boolean): void {
    this._prefs.update((p) => ({ ...p, showAgeRatings }));
    this.api.setShowAgeRatings(showAgeRatings).subscribe({ error: () => undefined });
  }

  setLanguage(language: Language): void {
    this._prefs.update((p) => ({ ...p, language }));
    this.api.setLanguage(language).subscribe({ error: () => undefined });
  }

  setShowGermanTitle(showGermanTitle: boolean): void {
    this._prefs.update((p) => ({ ...p, showGermanTitle }));
    this.api.setShowGermanTitle(showGermanTitle).subscribe({ error: () => undefined });
  }

  setViewMode(viewMode: ViewMode): void {
    this._prefs.update((p) => ({ ...p, viewMode }));
    this.api.setViewMode(viewMode).subscribe({ error: () => undefined });
  }

  setTilesPerRow(tilesPerRow: number): void {
    this._prefs.update((p) => ({ ...p, tilesPerRow }));
    this.api.setTilesPerRow(tilesPerRow).subscribe({ error: () => undefined });
  }

  /** Reflects the theme onto `color-scheme` so Material's `light-dark()` tokens follow it. */
  private applyTheme(theme: Theme): void {
    const scheme = theme === 'LIGHT' ? 'light' : theme === 'DARK' ? 'dark' : 'light dark';
    this.document.documentElement.style.colorScheme = scheme;
  }
}
