import { InjectionToken } from '@angular/core';

/**
 * Base path of the REST API as a **relative** URL (`../api/`). It is resolved by the browser
 * against the document base href, so it stays correct in every deployment without rebuilds:
 *
 *  - dev (ng serve at "/")            -> /api/…            (proxied to the backend)
 *  - local jar (served at "/app/")    -> /api/…
 *  - prod behind context path "/w2s"  -> /w2s/api/…
 *
 * It is deliberately relative (not absolute): Angular's built-in XSRF interceptor attaches the
 * X-XSRF-TOKEN header only to relative/same-origin requests, so CSRF-protected mutations from
 * the SPA work out of the box against Spring's cookie CSRF repository.
 */
export const API_BASE = new InjectionToken<string>('API_BASE', {
  providedIn: 'root',
  factory: () => '../api/',
});
