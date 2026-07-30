import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatDialog } from '@angular/material/dialog';
import { MatMenuModule } from '@angular/material/menu';
import { MatSnackBar } from '@angular/material/snack-bar';
import { TranslocoPipe, TranslocoService } from '@jsverse/transloco';
import { WatchlistApi } from '../../core/api/watchlist-api';
import { WatchlistStore } from '../../core/watchlist-store';
import { WatchlistStatus } from '../../core/models';
import { ConfirmDialog, ConfirmDialogData } from '../../shared/confirm-dialog/confirm-dialog';
import { ErrorAlert } from '../../shared/error-alert/error-alert';

/** Container: view the current user's watchlist status and import an IMDb CSV export (full sync). */
@Component({
  selector: 'app-watchlist-import-page',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [MatCardModule, MatButtonModule, MatMenuModule, ErrorAlert, TranslocoPipe],
  template: `
    <div class="watchlist-heading">
      <h1>{{ 'watchlist.title' | transloco }}</h1>
      <button type="button" matIconButton [matMenuTriggerFor]="menu" [attr.aria-label]="'watchlist.moreActions' | transloco">⋮</button>
      <mat-menu #menu="matMenu">
        <button type="button" mat-menu-item (click)="onRemoveWatched()">{{ 'watchlist.removeWatched' | transloco }}</button>
      </mat-menu>
    </div>
    <app-error-alert [message]="error()" />

    @if (loading()) {
      <mat-card>
        <mat-card-content>
          <p><span class="skeleton-bar skeleton-bar--narrow"></span></p>
        </mat-card-content>
      </mat-card>
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
    }

    <!-- The form/clear-button chrome doesn't depend on the fetch, so it renders (disabled) during
         loading too — only hidden on a failed load, when we don't know the current watchlist state. -->
    @if (loading() || status()) {
      <h2>{{ 'watchlist.importHeading' | transloco }}</h2>
      <p class="text-muted">
        {{ 'watchlist.importHelp' | transloco }}
      </p>
      <form (submit)="onImport($event)">
        <input
          type="file"
          accept=".csv"
          (change)="onFilePicked($any($event.target).files)"
          [disabled]="busy() || loading()" />
        <button matButton="filled" type="submit" [disabled]="busy() || loading() || !file()">{{ 'watchlist.import' | transloco }}</button>
      </form>

      <div class="watchlist-clear">
        <button matButton="outlined" (click)="onClear()" [disabled]="loading() || busy() || (status()?.count ?? 0) === 0">
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
    .watchlist-heading {
      display: flex;
      align-items: center;
      gap: 0.5rem;
    }
    .watchlist-heading h1 {
      margin: 0;
    }
  `,
})
export class WatchlistImportPage {
  private readonly api = inject(WatchlistApi);
  private readonly store = inject(WatchlistStore);
  private readonly snackBar = inject(MatSnackBar);
  private readonly transloco = inject(TranslocoService);
  private readonly dialog = inject(MatDialog);

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

  protected onRemoveWatched(): void {
    const data: ConfirmDialogData = {
      title: this.transloco.translate('watchlist.removeWatchedConfirmTitle'),
      message: this.transloco.translate('watchlist.removeWatchedConfirmMessage'),
      confirmLabel: this.transloco.translate('watchlist.removeWatched'),
    };
    this.dialog
      .open<ConfirmDialog, ConfirmDialogData, boolean>(ConfirmDialog, { data })
      .afterClosed()
      .subscribe((confirmed) => {
        if (confirmed) {
          this.removeWatched();
        }
      });
  }

  private removeWatched(): void {
    this.busy.set(true);
    this.error.set(null);
    this.api.clearSeen().subscribe({
      next: () => {
        this.busy.set(false);
        this.snackBar.open(this.transloco.translate('watchlist.watchedRemoved'), 'OK', { duration: 4000 });
        this.reload();
      },
      error: () => {
        this.busy.set(false);
        this.error.set(this.transloco.translate('watchlist.removeWatchedFailed'));
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
