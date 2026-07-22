import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { API_BASE } from '../api-base';
import { Me } from '../models';

@Injectable({ providedIn: 'root' })
export class AuthApi {
  private readonly http = inject(HttpClient);
  private readonly base = inject(API_BASE);

  me(): Observable<Me> {
    return this.http.get<Me>(`${this.base}me`);
  }

  /** POST to Spring's form-logout (CSRF header is added by Angular's XSRF interceptor). */
  logout(): Observable<unknown> {
    return this.http.post(`${this.base}../logout`, null);
  }
}
