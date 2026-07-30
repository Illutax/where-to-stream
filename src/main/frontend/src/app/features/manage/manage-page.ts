import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { MatSnackBar } from '@angular/material/snack-bar';
import { TranslocoPipe, TranslocoService } from '@jsverse/transloco';
import { ManageApi } from '../../core/api/manage-api';
import { ImdbId } from '../../core/domain';
import { ManagePage as ManagePageDto } from '../../core/models';
import { ErrorAlert } from '../../shared/error-alert/error-alert';
import { ManageTable } from '../../shared/manage-table/manage-table';

/** Container: loads the cache-management table and performs invalidate/scrape actions. */
@Component({
  selector: 'app-manage-page',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [ManageTable, ErrorAlert, TranslocoPipe],
  template: `
    <h1>{{ 'manage.title' | transloco }}</h1>
    @if (error()) {
      <app-error-alert [message]="error()" />
    } @else {
      <app-manage-table
        [rows]="page()?.rows ?? []"
        [needsScrapeCount]="page()?.needsScrapeCount ?? 0"
        [loading]="loading()"
        (invalidate)="onInvalidate($event)"
        (scrape)="onScrape()" />
    }
  `,
})
export class ManagePage {
  private readonly api = inject(ManageApi);
  private readonly snackBar = inject(MatSnackBar);
  private readonly transloco = inject(TranslocoService);

  protected readonly page = signal<ManagePageDto | null>(null);
  protected readonly loading = signal(true);
  protected readonly error = signal<string | null>(null);

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
        this.error.set(this.transloco.translate('manage.loadFailed'));
        this.loading.set(false);
      },
    });
  }

  protected onInvalidate(imdbIds: ImdbId[]): void {
    this.api.invalidate(imdbIds).subscribe({
      next: (result) => {
        this.snackBar.open(
          this.transloco.translate('manage.invalidated', { count: result.invalidated }),
          'OK',
          { duration: 4000 },
        );
        this.reload();
      },
      error: () => this.error.set(this.transloco.translate('manage.invalidateFailed')),
    });
  }

  protected onScrape(): void {
    this.api.scrape().subscribe({
      next: (result) => {
        this.snackBar.open(
          this.transloco.translate('manage.scraped', { count: result.scraped }),
          'OK',
          { duration: 4000 },
        );
        this.reload();
      },
      error: () => this.error.set(this.transloco.translate('manage.scrapeFailed')),
    });
  }
}
