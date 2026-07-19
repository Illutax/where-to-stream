import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { CatalogApi } from '../../core/api/catalog-api';
import { OverviewEntry } from '../../core/models';
import { CatalogTable } from '../../shared/catalog-table/catalog-table';
import { ErrorAlert } from '../../shared/error-alert/error-alert';
import { Loading } from '../../shared/loading/loading';

/** Container: loads the catalogue overview and hands it to the presentational table. */
@Component({
  selector: 'app-overview-page',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [CatalogTable, Loading, ErrorAlert],
  template: `
    <h1 class="h3 mb-3">Where 2 Stream</h1>
    @if (loading()) {
      <app-loading />
    } @else if (error()) {
      <app-error-alert [message]="error()" />
    } @else {
      <app-catalog-table [entries]="entries()" />
    }
  `,
})
export class OverviewPage {
  private readonly api = inject(CatalogApi);

  protected readonly entries = signal<OverviewEntry[]>([]);
  protected readonly loading = signal(true);
  protected readonly error = signal<string | null>(null);

  constructor() {
    this.api.getCatalog().subscribe({
      next: (entries) => {
        this.entries.set(entries);
        this.loading.set(false);
      },
      error: () => {
        this.error.set('Failed to load the catalogue.');
        this.loading.set(false);
      },
    });
  }
}
