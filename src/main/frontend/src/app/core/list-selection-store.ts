import { inject, Injectable, signal } from '@angular/core';
import { ListsApi } from './api/lists-api';

/**
 * Holds the currently selected list name for the navbar — the Angular equivalent of the
 * server's CommonAttributeService (which injected "selectedList" into every Thymeleaf model).
 * Loaded once on app start and updated when the user switches lists.
 */
@Injectable({ providedIn: 'root' })
export class ListSelectionStore {
  private readonly listsApi = inject(ListsApi);
  private readonly _current = signal<string | null>(null);

  /** The active list name, or null until loaded. */
  readonly current = this._current.asReadonly();

  load(): void {
    this.listsApi.getLists().subscribe({
      next: (selection) => this._current.set(selection.current),
    });
  }

  set(name: string): void {
    this._current.set(name);
  }
}
