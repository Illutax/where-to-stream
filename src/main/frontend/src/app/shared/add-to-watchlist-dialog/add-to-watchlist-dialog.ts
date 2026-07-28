import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MAT_DIALOG_DATA, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { TranslocoPipe } from '@jsverse/transloco';
import { AgeRatingStore } from '../../core/age-rating-store';
import { WatchlistApi } from '../../core/api/watchlist-api';
import { imdbUrl, posterFullUrl, releaseYearDisplay } from '../../core/domain';
import { GermanTitleStore } from '../../core/german-title-store';
import { ImdbSearchResult } from '../../core/models';
import { injectTitleMeta } from '../../core/title-meta';
import { AgeBadge } from '../age-badge/age-badge';

export type AddToWatchlistDialogData = ImdbSearchResult;

/**
 * Confirms adding a search hit to the watchlist. Fetches nothing from IMDb directly: the poster
 * and meta (age rating, German title) come from the app's own DB-cache-first
 * {@code /api/titles/{id}/poster} and {@code /meta} endpoints (via {@link injectTitleMeta}), same
 * as everywhere else in the app. Closes with {@code true} once the title is actually added, so the
 * caller (the search box) can flip that result's `onWatchlist` state locally.
 */
@Component({
  selector: 'app-add-to-watchlist-dialog',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [MatDialogModule, MatButtonModule, AgeBadge, TranslocoPipe],
  template: `
    <h2 mat-dialog-title>{{ displayTitle() }}</h2>
    <mat-dialog-content class="add-to-watchlist-content">
      @if (!hidden()) {
        <img
          class="add-to-watchlist-poster"
          [src]="posterFullUrl(data.imdbId)"
          [alt]="data.name"
          (error)="hidden.set(true)" />
      }
      <div class="add-to-watchlist-details">
        <p class="add-to-watchlist-year">{{ releaseYearDisplay(data.year) }}</p>
        @if (ageRatingStore.showAgeRatings() && meta()?.rating; as rating) {
          <app-age-badge [rating]="rating" />
        }
        <a [href]="imdbUrl(data.imdbId)" target="_blank" rel="noopener">{{ 'search.viewOnImdb' | transloco }}</a>
        @if (error()) {
          <p class="add-to-watchlist-error">{{ 'search.addFailed' | transloco }}</p>
        }
      </div>
    </mat-dialog-content>
    <mat-dialog-actions align="end">
      @if (data.onWatchlist) {
        <span class="add-to-watchlist-already">{{ 'search.alreadyOnWatchlist' | transloco }}</span>
        <button matButton (click)="dialogRef.close(false)">{{ 'common.dismiss' | transloco }}</button>
      } @else {
        <button matButton (click)="dialogRef.close(false)">{{ 'common.cancel' | transloco }}</button>
        <button matButton="filled" [disabled]="busy()" (click)="add()">{{ 'search.addToWatchlist' | transloco }}</button>
      }
    </mat-dialog-actions>
  `,
  styles: `
    .add-to-watchlist-content {
      display: flex;
      gap: 1rem;
      align-items: flex-start;
    }
    .add-to-watchlist-poster {
      flex: 0 0 auto;
      width: 120px;
      aspect-ratio: 2 / 3;
      object-fit: cover;
      border-radius: var(--mat-sys-corner-small);
    }
    .add-to-watchlist-details {
      display: flex;
      flex-direction: column;
      align-items: flex-start;
      gap: 0.5rem;
    }
    .add-to-watchlist-year {
      margin: 0;
      color: var(--mat-sys-on-surface-variant);
    }
    .add-to-watchlist-already {
      color: var(--mat-sys-on-surface-variant);
      margin-right: auto;
    }
    .add-to-watchlist-error {
      margin: 0;
      color: var(--mat-sys-error);
    }
  `,
})
export class AddToWatchlistDialog {
  protected readonly dialogRef = inject(MatDialogRef<AddToWatchlistDialog, boolean>);
  protected readonly data = inject<AddToWatchlistDialogData>(MAT_DIALOG_DATA);
  private readonly watchlistApi = inject(WatchlistApi);
  protected readonly ageRatingStore = inject(AgeRatingStore);
  private readonly germanTitleStore = inject(GermanTitleStore);

  protected readonly imdbUrl = imdbUrl;
  protected readonly posterFullUrl = posterFullUrl;
  protected readonly releaseYearDisplay = releaseYearDisplay;
  protected readonly hidden = signal(false);
  protected readonly busy = signal(false);
  protected readonly error = signal(false);

  protected readonly meta = injectTitleMeta(() => this.data.imdbId);
  protected readonly displayTitle = computed(
    () => (this.germanTitleStore.show() && this.meta()?.germanTitle) || this.data.name,
  );

  protected add(): void {
    this.busy.set(true);
    this.error.set(false);
    this.watchlistApi.addToWatchlist(this.data.imdbId, this.data.name, this.data.year).subscribe({
      next: () => this.dialogRef.close(true),
      error: () => {
        this.busy.set(false);
        this.error.set(true);
      },
    });
  }
}
