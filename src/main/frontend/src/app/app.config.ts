import { ApplicationConfig, provideBrowserGlobalErrorListeners } from '@angular/core';
import { provideRouter, withHashLocation } from '@angular/router';
import { provideHttpClient, withFetch, withInterceptors } from '@angular/common/http';

import { routes } from './app.routes';
import { unauthorizedInterceptor } from './core/unauthorized-interceptor';

export const appConfig: ApplicationConfig = {
  providers: [
    provideBrowserGlobalErrorListeners(),
    // Hash routing keeps the SPA agnostic to the prod context path (/w2s): deep links like
    // /w2s/app/#/provider/netflix resolve without a server-side wildcard fallback.
    provideRouter(routes, withHashLocation()),
    // XSRF protection is on by default (cookie XSRF-TOKEN, header X-XSRF-TOKEN) and matches
    // Spring's CookieCsrfTokenRepository; the 401 interceptor sends expired sessions to /login.
    provideHttpClient(withFetch(), withInterceptors([unauthorizedInterceptor])),
  ],
};
