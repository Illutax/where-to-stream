import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MAT_DIALOG_DATA, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { TranslocoPipe } from '@jsverse/transloco';
import { Observable } from 'rxjs';
import { imdbUrl, posterFullUrl, releaseYearDisplay } from '../../core/domain';
import { ImdbSearchResult } from '../../core/models';
import { injectTitleMeta } from '../../core/title-meta';
import { UserPrefsStore } from '../../core/user-prefs-store';
import { AgeBadge } from '../age-badge/age-badge';

export interface AddToWatchlistDialogData extends ImdbSearchResult {
  /**
   * Performs the actual "add to watchlist" mutation.
   * Owned and constructed by the caller (the smart `ImdbSearchBox`, which already injects
   * `WatchlistApi`) — this dialog only triggers it and reflects busy/error state,
   * it never talks to `WatchlistApi` itself.
   */
  submit: () => Observable<void>;
}

/**
 * Confirms adding a search hit to the watchlist.
 * Fetches nothing from IMDb directly: the poster and meta (age rating, German title) come from
 * the app's own DB-cache-first {@code /api/titles/{id}/poster} and {@code /meta} endpoints (via
 * {@link injectTitleMeta}), same as everywhere else in the app (the same established exception
 * `TitleCell`/`TitleTile` already use — a component fetching its own per-row metadata isn't
 * the same thing as owning a mutation).
 * Closes with {@code true} once the title is actually added, so the caller (the search box) can
 * flip that result's `onWatchlist` state locally.
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
        @if (userPrefsStore.showAgeRatings() && meta()?.rating; as rating) {
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
  protected readonly userPrefsStore = inject(UserPrefsStore);

  protected readonly imdbUrl = imdbUrl;
  protected readonly posterFullUrl = posterFullUrl;
  protected readonly releaseYearDisplay = releaseYearDisplay;
  protected readonly hidden = signal(false);
  protected readonly busy = signal(false);
  protected readonly error = signal(false);

  protected readonly meta = injectTitleMeta(() => this.data.imdbId);
  protected readonly displayTitle = computed(
    () => (this.userPrefsStore.showGermanTitle() && this.meta()?.germanTitle) || this.data.name,
  );

  protected add(): void {
    this.busy.set(true);
    this.error.set(false);
    this.data.submit().subscribe({
      next: () => this.dialogRef.close(true),
      error: () => {
        this.busy.set(false);
        this.error.set(true);
      },
    });
  }
}
