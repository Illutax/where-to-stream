import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { API_BASE } from '../api-base';
import { AdminUser, CreateUserRequest, UpdateUserRequest } from '../models';

@Injectable({ providedIn: 'root' })
export class AdminUsersApi {
  private readonly http = inject(HttpClient);
  private readonly base = inject(API_BASE);

  list(): Observable<AdminUser[]> {
    return this.http.get<AdminUser[]>(`${this.base}admin/users`);
  }

  create(request: CreateUserRequest): Observable<AdminUser> {
    return this.http.post<AdminUser>(`${this.base}admin/users`, request);
  }

  update(id: string, request: UpdateUserRequest): Observable<AdminUser> {
    return this.http.put<AdminUser>(`${this.base}admin/users/${id}`, request);
  }

  delete(id: string): Observable<void> {
    return this.http.delete<void>(`${this.base}admin/users/${id}`);
  }

  resetPassword(id: string, newPassword: string): Observable<void> {
    return this.http.post<void>(`${this.base}admin/users/${id}/password`, { newPassword });
  }
}
