import { TranslocoTestingModule, TranslocoTestingOptions } from '@jsverse/transloco';
import en from '../../i18n/en.json';

/**
 * Provides Transloco to component specs with the real English translations preloaded, so specs that
 * assert on rendered text keep asserting the English strings.
 */
export function translocoTesting(options: TranslocoTestingOptions = {}) {
  return TranslocoTestingModule.forRoot({
    langs: { en },
    translocoConfig: { availableLangs: ['en', 'de'], defaultLang: 'en' },
    preloadLangs: true,
    ...options,
  });
}
