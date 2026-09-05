import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { API_BASE } from '../api-base';

/**
 * Entering and leaving an admin impersonation (ADR-0020).
 *
 * <p>The two calls sit on deliberately different paths. Starting is an admin action and lives under
 * `/api/admin/`. Leaving does not: while switched, the session carries the target's roles and no
 * longer counts as an admin, so an exit behind the admin rule would be closed to the only session
 * that needs it.
 */
@Injectable({ providedIn: 'root' })
export class ImpersonationApi {
  private readonly http = inject(HttpClient);
  private readonly base = inject(API_BASE);

  switchTo(username: string): Observable<void> {
    return this.http.post<void>(
      `${this.base}admin/impersonate?username=${encodeURIComponent(username)}`,
      null,
    );
  }

  exit(): Observable<void> {
    return this.http.post<void>(`${this.base}impersonate/exit`, null);
  }
}
