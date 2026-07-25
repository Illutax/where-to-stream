import { DOCUMENT } from '@angular/common';
import { inject, Injectable, signal } from '@angular/core';
import { ThemeApi } from './api/theme-api';
import { Theme } from './models';

/**
 * Holds and applies the user's UI colour-scheme preference. The theme is applied by setting the
 * `color-scheme` property on the document root — Material's M3 tokens use the CSS `light-dark()`
 * function, so `light`/`dark` force one scheme and `light dark` (SYSTEM) follows the OS setting.
 * Loaded from the account on app start (via {@code /api/me}) and persisted on change.
 */
@Injectable({ providedIn: 'root' })
export class ThemeStore {
  private readonly themeApi = inject(ThemeApi);
  private readonly document = inject(DOCUMENT);
  private readonly _theme = signal<Theme>('SYSTEM');

  readonly theme = this._theme.asReadonly();

  /** Adopt the theme loaded from the server without persisting it back. */
  init(theme: Theme): void {
    this._theme.set(theme);
    this.apply(theme);
  }

  /** User-initiated change: apply immediately and persist it for the account. */
  set(theme: Theme): void {
    this._theme.set(theme);
    this.apply(theme);
    this.themeApi.setTheme(theme).subscribe({ error: () => undefined });
  }

  private apply(theme: Theme): void {
    const scheme = theme === 'LIGHT' ? 'light' : theme === 'DARK' ? 'dark' : 'light dark';
    this.document.documentElement.style.colorScheme = scheme;
  }
}
