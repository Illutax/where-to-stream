import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { API_BASE } from '../api-base';
import { CatalogPage } from '../models';

@Injectable({ providedIn: 'root' })
export class CatalogApi {
  private readonly http = inject(HttpClient);
  private readonly base = inject(API_BASE);

  getCatalog(): Observable<CatalogPage> {
    return this.http.get<CatalogPage>(`${this.base}catalog`);
  }
}
