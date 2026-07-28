import { ChangeDetectionStrategy, Component, input, output } from '@angular/core';
import { TranslocoPipe } from '@jsverse/transloco';
import { ImdbId, releaseYearDisplay } from '../../core/domain';
import { ImdbSearchResult } from '../../core/models';
import { PosterThumb } from '../poster-thumb/poster-thumb';

/**
 * The IMDb search results dropdown: purely presentational, renders whatever `results` it's given
 * and emits `resultSelected` on a click — no HTTP, no dialog. The parent decides when to mount this (so an
 * empty `results` array here always means "searched, found nothing", never "haven't searched yet").
 */
@Component({
  selector: 'app-imdb-search-results',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [PosterThumb, TranslocoPipe],
  template: `
    @if (results().length > 0) {
      <ul class="search-results">
        @for (result of results(); track result.imdbId) {
          <li>
            <button type="button" class="search-result" (click)="resultSelected.emit(result.imdbId)">
              <app-poster-thumb [imdbId]="result.imdbId" [name]="result.name" />
              <span class="search-result-name">{{ result.name }}</span>
              <span class="search-result-year">{{ releaseYearDisplay(result.year) }}</span>
              @if (result.onWatchlist) {
                <span class="search-result-onwatchlist" [title]="'search.alreadyOnWatchlist' | transloco">✓</span>
              }
            </button>
          </li>
        }
      </ul>
    } @else {
      <p class="search-no-results">{{ 'search.noResults' | transloco }}</p>
    }
  `,
  styles: `
    .search-results {
      list-style: none;
      margin: 0;
      padding: 0.25rem 0;
      max-height: 60vh;
      overflow-y: auto;
    }
    .search-result {
      display: flex;
      align-items: center;
      gap: 0.5rem;
      width: 100%;
      padding: 0.4rem 0.75rem;
      border: none;
      background: none;
      cursor: pointer;
      text-align: left;
      font: inherit;
      color: var(--mat-sys-on-surface);
    }
    .search-result:hover,
    .search-result:focus-visible {
      background: var(--mat-sys-surface-variant);
    }
    .search-result-name {
      flex: 1 1 auto;
      overflow: hidden;
      text-overflow: ellipsis;
      white-space: nowrap;
    }
    .search-result-year {
      flex: 0 0 auto;
      color: var(--mat-sys-on-surface-variant);
      font-size: 0.85em;
    }
    .search-result-onwatchlist {
      flex: 0 0 auto;
      color: var(--mat-sys-primary);
      font-weight: 600;
    }
    .search-no-results {
      margin: 0;
      padding: 0.75rem;
      color: var(--mat-sys-on-surface-variant);
    }
  `,
})
export class ImdbSearchResults {
  readonly results = input.required<ImdbSearchResult[]>();
  readonly resultSelected = output<ImdbId>();
  protected readonly releaseYearDisplay = releaseYearDisplay;
}
