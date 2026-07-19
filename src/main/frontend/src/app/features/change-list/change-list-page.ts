import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { ListsApi } from '../../core/api/lists-api';
import { ListSelectionStore } from '../../core/list-selection-store';
import { ListSelection } from '../../core/models';
import { ErrorAlert } from '../../shared/error-alert/error-alert';
import { ListPicker } from '../../shared/list-picker/list-picker';
import { Loading } from '../../shared/loading/loading';

/** Container: view and switch the active IMDb list. */
@Component({
  selector: 'app-change-list-page',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [ListPicker, Loading, ErrorAlert],
  template: `
    <h1 class="h3 mb-3">Change list</h1>
    @if (notice(); as n) {
      <div class="alert alert-success" role="alert">{{ n }}</div>
    }
    <app-error-alert [message]="error()" />
    @if (loading()) {
      <app-loading />
    } @else if (selection(); as s) {
      <app-list-picker
        [current]="s.current"
        [available]="s.available"
        [disabled]="changing()"
        (change)="onChange($event)" />
      @if (changing()) {
        <p class="text-secondary mt-2">Switching list and pre-caching… this can take a while.</p>
      }
    }
  `,
})
export class ChangeListPage {
  private readonly api = inject(ListsApi);
  private readonly store = inject(ListSelectionStore);

  protected readonly selection = signal<ListSelection | null>(null);
  protected readonly loading = signal(true);
  protected readonly changing = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly notice = signal<string | null>(null);

  constructor() {
    this.reload();
  }

  private reload(): void {
    this.loading.set(true);
    this.api.getLists().subscribe({
      next: (selection) => {
        this.selection.set(selection);
        this.loading.set(false);
      },
      error: () => {
        this.error.set('Failed to load the lists.');
        this.loading.set(false);
      },
    });
  }

  protected onChange(name: string): void {
    this.changing.set(true);
    this.error.set(null);
    this.notice.set(null);
    this.api.changeList(name).subscribe({
      next: (result) => {
        this.changing.set(false);
        this.notice.set(`Switched to "${result.selected}" (${result.cached} titles cached).`);
        this.store.set(result.selected);
        this.reload();
      },
      error: (err) => {
        this.changing.set(false);
        this.error.set(err?.error?.detail ?? 'Could not switch list.');
      },
    });
  }
}
