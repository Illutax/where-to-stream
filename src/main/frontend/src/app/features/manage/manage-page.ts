import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { ManageApi } from '../../core/api/manage-api';
import { ManagePage as ManagePageDto } from '../../core/models';
import { ErrorAlert } from '../../shared/error-alert/error-alert';
import { Loading } from '../../shared/loading/loading';
import { ManageTable } from '../../shared/manage-table/manage-table';

/** Container: loads the cache-management table and performs invalidate/scrape actions. */
@Component({
  selector: 'app-manage-page',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [ManageTable, Loading, ErrorAlert],
  template: `
    <h1 class="h3 mb-3">Manage cache</h1>
    @if (notice(); as n) {
      <div class="alert alert-success" role="alert">{{ n }}</div>
    }
    @if (loading()) {
      <app-loading />
    } @else if (error()) {
      <app-error-alert [message]="error()" />
    } @else if (page(); as p) {
      <app-manage-table
        [rows]="p.rows"
        [needsScrapeCount]="p.needsScrapeCount"
        (invalidate)="onInvalidate($event)"
        (scrape)="onScrape()" />
    }
  `,
})
export class ManagePage {
  private readonly api = inject(ManageApi);

  protected readonly page = signal<ManagePageDto | null>(null);
  protected readonly loading = signal(true);
  protected readonly error = signal<string | null>(null);
  protected readonly notice = signal<string | null>(null);

  constructor() {
    this.reload();
  }

  private reload(): void {
    this.loading.set(true);
    this.error.set(null);
    this.api.getManagePage().subscribe({
      next: (page) => {
        this.page.set(page);
        this.loading.set(false);
      },
      error: () => {
        this.error.set('Failed to load the cache overview.');
        this.loading.set(false);
      },
    });
  }

  protected onInvalidate(imdbIds: string[]): void {
    this.api.invalidate(imdbIds).subscribe({
      next: (result) => {
        this.notice.set(`Invalidated ${result.invalidated} cache row(s).`);
        this.reload();
      },
      error: () => this.error.set('Invalidation failed.'),
    });
  }

  protected onScrape(): void {
    this.api.scrape().subscribe({
      next: (result) => {
        this.notice.set(`(Re-)scraped ${result.scraped} title(s).`);
        this.reload();
      },
      error: () => this.error.set('Scraping failed.'),
    });
  }
}
