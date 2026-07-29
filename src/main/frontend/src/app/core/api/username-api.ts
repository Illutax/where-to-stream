import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { API_BASE } from '../api-base';

/**
 * Renames the current user's login username (PUT /api/me/username).
 * The server invalidates the session on success, so the caller must send the user back to the
 * login page afterwards.
 */
@Injectable({ providedIn: 'root' })
export class UsernameApi {
  private readonly http = inject(HttpClient);
  private readonly base = inject(API_BASE);

  setUsername(username: string): Observable<void> {
    return this.http.put<void>(`${this.base}me/username`, { username });
  }
}
