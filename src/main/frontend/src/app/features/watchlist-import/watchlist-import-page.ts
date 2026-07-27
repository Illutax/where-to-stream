import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatSnackBar } from '@angular/material/snack-bar';
import { TranslocoPipe, TranslocoService } from '@jsverse/transloco';
import { WatchlistApi } from '../../core/api/watchlist-api';
import { WatchlistStore } from '../../core/watchlist-store';
import { WatchlistStatus } from '../../core/models';
import { ErrorAlert } from '../../shared/error-alert/error-alert';
import { Loading } from '../../shared/loading/loading';

/** Container: view the current user's watchlist status and import an IMDb CSV export (full sync). */
@Component({
  selector: 'app-watchlist-import-page',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [MatCardModule, MatButtonModule, Loading, ErrorAlert, TranslocoPipe],
  template: `
    <h1>{{ 'watchlist.title' | transloco }}</h1>
    <app-error-alert [message]="error()" />

    @if (loading()) {
      <app-loading />
    } @else if (status(); as s) {
      <mat-card>
        <mat-card-content>
          <p>
            <span>{{ s.count }}</span> {{ 'watchlist.titlesOnList' | transloco }}
            @if (s.lastImportedAt) {
              <span>{{ 'watchlist.lastImport' | transloco: { date: s.lastImportedAt } }}</span>
            }
          </p>
        </mat-card-content>
      </mat-card>

      <h2>{{ 'watchlist.importHeading' | transloco }}</h2>
      <p class="text-muted">
        {{ 'watchlist.importHelp' | transloco }}
      </p>
      <form (submit)="onImport($event)">
        <input
          type="file"
          accept=".csv"
          (change)="onFilePicked($any($event.target).files)"
          [disabled]="busy()" />
        <button matButton="filled" type="submit" [disabled]="busy() || !file()">{{ 'watchlist.import' | transloco }}</button>
      </form>

      <div class="watchlist-clear">
        <button matButton="outlined" (click)="onClear()" [disabled]="busy() || s.count === 0">
          {{ 'watchlist.clear' | transloco }}
        </button>
      </div>
      @if (busy()) {
        <p class="text-muted">{{ 'watchlist.working' | transloco }}</p>
      }
    }
  `,
  styles: `
    .watchlist-clear {
      margin-top: 1.5rem;
    }
  `,
})
export class WatchlistImportPage {
  private readonly api = inject(WatchlistApi);
  private readonly store = inject(WatchlistStore);
  private readonly snackBar = inject(MatSnackBar);
  private readonly transloco = inject(TranslocoService);

  protected readonly status = signal<WatchlistStatus | null>(null);
  protected readonly loading = signal(true);
  protected readonly busy = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly file = signal<File | null>(null);

  constructor() {
    this.reload();
  }

  private reload(): void {
    this.loading.set(true);
    this.api.getStatus().subscribe({
      next: (status) => {
        this.status.set(status);
        this.store.set(status.count);
        this.loading.set(false);
      },
      error: () => {
        this.error.set(this.transloco.translate('watchlist.loadFailed'));
        this.loading.set(false);
      },
    });
  }

  protected onFilePicked(files: FileList | null): void {
    this.file.set(files && files.length > 0 ? files[0] : null);
  }

  protected onImport(event: Event): void {
    event.preventDefault();
    const file = this.file();
    if (!file) {
      return;
    }
    this.busy.set(true);
    this.error.set(null);
    this.api.import(file).subscribe({
      next: (result) => {
        this.busy.set(false);
        this.file.set(null);
        this.snackBar.open(
          this.transloco.translate('watchlist.imported', {
            added: result.added,
            updated: result.updated,
            removed: result.removed,
            total: result.total,
          }),
          'OK',
          { duration: 4000 },
        );
        this.reload();
      },
      error: (err) => {
        this.busy.set(false);
        this.error.set(err?.error?.detail ?? this.transloco.translate('watchlist.importFailed'));
      },
    });
  }

  protected onClear(): void {
    this.busy.set(true);
    this.error.set(null);
    this.api.clear().subscribe({
      next: () => {
        this.busy.set(false);
        this.snackBar.open(this.transloco.translate('watchlist.cleared'), 'OK', { duration: 4000 });
        this.reload();
      },
      error: () => {
        this.busy.set(false);
        this.error.set(this.transloco.translate('watchlist.clearFailed'));
      },
    });
  }
}
