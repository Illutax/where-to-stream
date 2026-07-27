import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { API_BASE } from '../api-base';
import { Language } from '../models';

/** Persists the current user's own UI-language preference (PUT /api/me/language). */
@Injectable({ providedIn: 'root' })
export class LanguageApi {
  private readonly http = inject(HttpClient);
  private readonly base = inject(API_BASE);

  setLanguage(language: Language): Observable<void> {
    return this.http.put<void>(`${this.base}me/language`, { language });
  }
}
