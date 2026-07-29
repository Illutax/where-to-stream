import { computed, inject, Injectable, signal } from '@angular/core';
import { catchError, Observable, of, shareReplay, tap } from 'rxjs';
import { AuthApi } from './api/auth-api';
import { Me } from './models';

/**
 * Holds the current principal for the SPA (navbar, admin guard, admin-only UI). Loaded once on
 * app start via {@code GET /api/me}.
 */
@Injectable({ providedIn: 'root' })
export class AuthStore {
  private readonly authApi = inject(AuthApi);
  private readonly _me = signal<Me | null>(null);

  readonly me = this._me.asReadonly();
  readonly username = computed(() => this._me()?.username ?? null);
  readonly isAdmin = computed(() => this._me()?.admin ?? false);
  readonly authenticated = computed(() => this._me()?.authenticated ?? false);
  /** True when posters come from TMDB, so the SPA shows the required TMDB attribution footer. */
  readonly tmdbAttribution = computed(() => this._me()?.tmdbAttribution ?? false);

  /**
   * Fetches the principal and updates {@link me}. Returns the (shared) result so a caller that
   * needs to run a one-shot action once loading completes (e.g. seeding {@code UserPrefsStore})
   * can subscribe directly instead of watching {@link me} via `effect()` — see ADR-0013 for why
   * that distinction matters. Callers that only need the side effect (updating this store) can
   * ignore the return value, exactly as before.
   */
  load(): Observable<Me | null> {
    const me$ = this.authApi.me().pipe(
      catchError(() => of(null)),
      tap((me) => this._me.set(me)),
      shareReplay(1),
    );
    me$.subscribe();
    return me$;
  }

  logout(): void {
    this.authApi.logout().subscribe({
      // After the session is cleared, land on the (server-rendered) login page.
      next: () => this.redirectToLogin(),
      error: () => this.redirectToLogin(),
    });
  }

  private redirectToLogin(): void {
    window.location.href = new URL('../login', document.baseURI).toString();
  }
}
