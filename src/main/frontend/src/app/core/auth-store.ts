import { computed, inject, Injectable, signal } from '@angular/core';
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

  load(): void {
    this.authApi.me().subscribe({
      next: (me) => this._me.set(me),
      error: () => this._me.set(null),
    });
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
