import { ApplicationConfig, provideBrowserGlobalErrorListeners } from '@angular/core';
import { provideRouter, withHashLocation } from '@angular/router';
import { provideHttpClient, withFetch } from '@angular/common/http';

import { routes } from './app.routes';

export const appConfig: ApplicationConfig = {
  providers: [
    provideBrowserGlobalErrorListeners(),
    // Hash routing keeps the SPA agnostic to the prod context path (/w2s): deep links like
    // /w2s/app/#/provider/netflix resolve without a server-side wildcard fallback.
    provideRouter(routes, withHashLocation()),
    provideHttpClient(withFetch()),
  ],
};
