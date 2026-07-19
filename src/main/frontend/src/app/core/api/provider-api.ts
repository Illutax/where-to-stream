import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { API_BASE } from '../api-base';
import { ProviderPage } from '../models';

@Injectable({ providedIn: 'root' })
export class ProviderApi {
  private readonly http = inject(HttpClient);
  private readonly base = inject(API_BASE);

  getProvider(key: string): Observable<ProviderPage> {
    return this.http.get<ProviderPage>(`${this.base}providers/${key}`);
  }
}
