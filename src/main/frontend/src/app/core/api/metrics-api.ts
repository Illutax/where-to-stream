import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { API_BASE } from '../api-base';
import { InstanceMetrics } from '../models';

/** ADMIN-only instance metrics. The path is the authorisation: the server gates /api/admin/**. */
@Injectable({ providedIn: 'root' })
export class MetricsApi {
  private readonly http = inject(HttpClient);
  private readonly base = inject(API_BASE);

  getMetrics(): Observable<InstanceMetrics> {
    return this.http.get<InstanceMetrics>(`${this.base}admin/metrics`);
  }
}
