import { HttpInterceptorFn } from '@angular/common/http';
import { catchError, throwError } from 'rxjs';

/**
 * On a 401 (expired/absent session) send the browser to the server-rendered login page. The SPA
 * is session-authenticated, so recovering means re-authenticating there.
 */
export const unauthorizedInterceptor: HttpInterceptorFn = (req, next) =>
  next(req).pipe(
    catchError((error) => {
      if (error?.status === 401) {
        window.location.href = new URL('../login', document.baseURI).toString();
      }
      return throwError(() => error);
    }),
  );
