import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { API_BASE } from '../api-base';
import { ChangeListResult, ListSelection } from '../models';

@Injectable({ providedIn: 'root' })
export class ListsApi {
  private readonly http = inject(HttpClient);
  private readonly base = inject(API_BASE);

  getLists(): Observable<ListSelection> {
    return this.http.get<ListSelection>(`${this.base}lists`);
  }

  changeList(name: string): Observable<ChangeListResult> {
    return this.http.put<ChangeListResult>(`${this.base}lists/selection`, { name });
  }
}
