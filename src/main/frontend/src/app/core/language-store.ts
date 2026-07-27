import { inject, Injectable, signal } from '@angular/core';
import { LanguageApi } from './api/language-api';
import { Language } from './models';

/**
 * Holds the user's UI-language preference. Loaded from the account on app start (via {@code /api/me})
 * and persisted on change. The active Transloco language is driven off {@link language} in the app
 * shell.
 */
@Injectable({ providedIn: 'root' })
export class LanguageStore {
  private readonly languageApi = inject(LanguageApi);
  private readonly _language = signal<Language>('EN');

  readonly language = this._language.asReadonly();

  /** Adopt the language loaded from the server without persisting it back. */
  init(language: Language): void {
    this._language.set(language);
  }

  /** User-initiated change: apply immediately and persist it for the account. */
  set(language: Language): void {
    this._language.set(language);
    this.languageApi.setLanguage(language).subscribe({ error: () => undefined });
  }
}
