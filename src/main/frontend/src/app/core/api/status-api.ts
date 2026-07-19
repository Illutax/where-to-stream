import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { API_BASE } from '../api-base';
import { Status } from '../models';

@Injectable({ providedIn: 'root' })
export class StatusApi {
  private readonly http = inject(HttpClient);
  private readonly base = inject(API_BASE);

  getStatus(): Observable<Status> {
    return this.http.get<Status>(`${this.base}status`);
  }
}
