import { InjectionToken } from '@angular/core';

/**
 * Absolute base URL of the REST API, derived from the document base URI so it is correct in
 * every deployment without rebuilds:
 *
 *  - dev (ng serve at "/")            -> http://localhost:4200/api/  (proxied to the backend)
 *  - local jar (served at "/app/")    -> http://host:8001/api/
 *  - prod behind context path "/w2s"  -> http://host/w2s/api/
 *
 * The app is always served at "<context>/app/" (base href "./"), and the API lives one level
 * up at "<context>/api/", hence "../api/" relative to document.baseURI.
 */
export const API_BASE = new InjectionToken<string>('API_BASE', {
  providedIn: 'root',
  factory: () => new URL('../api/', document.baseURI).toString(),
});
