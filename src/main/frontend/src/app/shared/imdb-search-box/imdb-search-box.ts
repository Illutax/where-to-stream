import { ConnectedPosition, OverlayModule } from '@angular/cdk/overlay';
import {
  ChangeDetectionStrategy,
  Component,
  ElementRef,
  inject,
  signal,
  viewChild,
} from '@angular/core';
import { takeUntilDestroyed, toObservable } from '@angular/core/rxjs-interop';
import { MatButtonModule } from '@angular/material/button';
import { MatDialog } from '@angular/material/dialog';
import { TranslocoPipe } from '@jsverse/transloco';
import { debounceTime, distinctUntilChanged, of, switchMap } from 'rxjs';
import { ImdbSearchApi } from '../../core/api/imdb-search-api';
import { WatchlistApi } from '../../core/api/watchlist-api';
import { ImdbId } from '../../core/domain';
import { ImdbSearchResult } from '../../core/models';
import { AddToWatchlistDialog, AddToWatchlistDialogData } from '../add-to-watchlist-dialog/add-to-watchlist-dialog';
import { ImdbSearchResults } from '../imdb-search-results/imdb-search-results';

/** Matches the "max 1 request/second" requirement — at most one search request per second. */
const DEBOUNCE_MS = 1000;
const MIN_QUERY_LENGTH = 2;

/**
 * The navbar search: a magnifying-glass icon that expands into a text field, debounced (see
 * {@link DEBOUNCE_MS}) against IMDb. The only smart piece of the feature — owns the HTTP call and
 * the results list; rendering is delegated to the dumb {@link ImdbSearchResults}, and picking a
 * result opens {@link AddToWatchlistDialog}. On a successful add, patches that one result's
 * `onWatchlist` flag locally rather than re-searching.
 */
@Component({
  selector: 'app-imdb-search-box',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [OverlayModule, ImdbSearchResults, MatButtonModule, TranslocoPipe],
  template: `
    <div class="search-box" cdkOverlayOrigin #origin="cdkOverlayOrigin">
      <button
        type="button"
        matIconButton
        (click)="toggleOpen()"
        [attr.aria-label]="(open() ? 'search.close' : 'search.open') | transloco">
        {{ open() ? '✕' : '🔍' }}
      </button>
      @if (open()) {
        <input
          #input
          type="search"
          class="search-input"
          [placeholder]="'search.placeholder' | transloco"
          [attr.aria-label]="'search.placeholder' | transloco"
          [value]="query()"
          (input)="query.set($any($event.target).value)"
          (keydown.escape)="close()" />
      }
    </div>

    <ng-template
      cdkConnectedOverlay
      [cdkConnectedOverlayOrigin]="origin"
      [cdkConnectedOverlayOpen]="showResults()"
      [cdkConnectedOverlayPositions]="positions"
      [cdkConnectedOverlayHasBackdrop]="true"
      cdkConnectedOverlayBackdropClass="cdk-overlay-transparent-backdrop"
      (backdropClick)="close()">
      <div class="search-results-panel">
        <app-imdb-search-results [results]="results()" (resultSelected)="onSelect($event)" />
      </div>
    </ng-template>
  `,
  styles: `
    .search-box {
      display: flex;
      align-items: center;
      gap: 0.25rem;
    }
    .search-input {
      width: 12rem;
      max-width: 40vw;
      padding: 0.3rem 0.5rem;
      border: none;
      border-bottom: 1px solid var(--mat-sys-outline);
      background: transparent;
      color: inherit;
      font: inherit;
    }
    .search-input:focus {
      outline: none;
      border-bottom-color: var(--mat-sys-primary);
    }
    .search-results-panel {
      background: var(--mat-sys-surface);
      color: var(--mat-sys-on-surface);
      border-radius: var(--mat-sys-corner-small);
      box-shadow: var(--mat-sys-level3);
      min-width: 16rem;
      max-width: 90vw;
    }
  `,
})
export class ImdbSearchBox {
  private readonly imdbSearchApi = inject(ImdbSearchApi);
  private readonly watchlistApi = inject(WatchlistApi);
  private readonly dialog = inject(MatDialog);
  private readonly inputRef = viewChild<ElementRef<HTMLInputElement>>('input');

  protected readonly open = signal(false);
  protected readonly query = signal('');
  protected readonly results = signal<ImdbSearchResult[]>([]);

  protected readonly showResults = signal(false);

  protected readonly positions: ConnectedPosition[] = [
    { originX: 'start', originY: 'bottom', overlayX: 'start', overlayY: 'top', offsetY: 4 },
    { originX: 'end', originY: 'bottom', overlayX: 'end', overlayY: 'top', offsetY: 4 },
  ];

  constructor() {
    toObservable(this.query)
      .pipe(
        debounceTime(DEBOUNCE_MS),
        distinctUntilChanged(),
        switchMap((q) => {
          const trimmed = q.trim();
          this.showResults.set(this.open() && trimmed.length >= MIN_QUERY_LENGTH);
          return trimmed.length >= MIN_QUERY_LENGTH ? this.imdbSearchApi.search(trimmed) : of([]);
        }),
        takeUntilDestroyed(),
      )
      .subscribe((results) => this.results.set(results));
  }

  protected toggleOpen(): void {
    if (this.open()) {
      this.close();
    } else {
      this.open.set(true);
      queueMicrotask(() => this.inputRef()?.nativeElement.focus());
    }
  }

  protected close(): void {
    this.open.set(false);
    this.showResults.set(false);
    this.query.set('');
    this.results.set([]);
  }

  protected onSelect(imdbId: ImdbId): void {
    const result = this.results().find((r) => r.imdbId === imdbId);
    if (!result) {
      return;
    }
    const data: AddToWatchlistDialogData = {
      ...result,
      submit: () => this.watchlistApi.addToWatchlist(result.imdbId, result.name, result.year),
    };
    this.dialog
      .open<AddToWatchlistDialog, AddToWatchlistDialogData, boolean>(AddToWatchlistDialog, { data })
      .afterClosed()
      .subscribe((added) => {
        if (added) {
          this.results.update((list) => list.map((r) => (r.imdbId === imdbId ? { ...r, onWatchlist: true } : r)));
        }
      });
  }
}
