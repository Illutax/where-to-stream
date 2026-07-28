import { ChangeDetectionStrategy, Component, computed, inject, input, output, signal } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatButtonToggleModule } from '@angular/material/button-toggle';
import { Sort } from '@angular/material/sort';
import { TranslocoPipe } from '@jsverse/transloco';
import { ImdbId } from '../../core/domain';
import { UserPrefsStore } from '../../core/user-prefs-store';
import { sortRows } from '../sort/table-sort';
import { TileEntry } from '../../core/tile-entry';
import { TitleTile } from '../title-tile/title-tile';

/** Tiles-per-row choices the segmented control offers (mirrors the design's 2-6 range). */
const TILE_COUNT_OPTIONS = [2, 3, 4, 5, 6] as const;

/**
 * Presentational poster-tile grid: the alternative to the sortable Material tables. Sorting reuses
 * the exact same {@link sortRows} function the tables use, so seen/name/year/added semantics are
 * identical across both views. Tiles-per-row and the fixed dark-scrim-over-artwork tile styling
 * live in {@link TitleTile}; this component only owns the toolbar (sort, per-row, watched count)
 * and the responsive grid layout.
 */
@Component({
  selector: 'app-title-grid',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [MatButtonModule, MatButtonToggleModule, TitleTile, TranslocoPipe],
  template: `
    <div class="grid-toolbar">
      <mat-button-toggle-group
        class="sort-group"
        [value]="sort().active"
        (change)="setSortField($event.value)"
        hideSingleSelectionIndicator
        [attr.aria-label]="'grid.sortBy' | transloco">
        <mat-button-toggle value="rated">{{ 'table.seen' | transloco }}</mat-button-toggle>
        <mat-button-toggle value="title">{{ 'table.title' | transloco }}</mat-button-toggle>
        <mat-button-toggle value="year">{{ 'table.year' | transloco }}</mat-button-toggle>
        <mat-button-toggle value="added">{{ 'table.added' | transloco }}</mat-button-toggle>
      </mat-button-toggle-group>
      <button
        type="button"
        class="sort-direction-toggle"
        matIconButton
        (click)="toggleDirection()"
        [attr.aria-label]="'grid.sortDirection' | transloco">
        {{ sort().direction === 'desc' ? '↓' : '↑' }}
      </button>

      <span class="toolbar-spacer"></span>

      <span class="per-row-label">{{ 'grid.perRowLabel' | transloco }}</span>
      <mat-button-toggle-group
        class="per-row-group"
        [value]="userPrefs.tilesPerRow()"
        (change)="userPrefs.setTilesPerRow($event.value)"
        hideSingleSelectionIndicator
        [attr.aria-label]="'grid.perRowLabel' | transloco">
        @for (n of tileCountOptions; track n) {
          <mat-button-toggle [value]="n">{{ n }}</mat-button-toggle>
        }
      </mat-button-toggle-group>

      <span class="watched-counter">{{ 'grid.watchedOf' | transloco: { watched: watchedCount(), total: total() } }}</span>
    </div>

    <div class="tile-grid" [style.--tiles-per-row]="userPrefs.tilesPerRow()">
      @for (entry of sorted(); track entry.imdbId) {
        <app-title-tile
          [imdbId]="entry.imdbId"
          [name]="entry.name"
          [year]="entry.year"
          [added]="entry.added"
          [isRated]="entry.isRated"
          [recentlyChanged]="entry.imdbId === recentlyChangedId()"
          (seenToggle)="seenToggle.emit($event)" />
      } @empty {
        <p class="text-muted">{{ 'table.noEntries' | transloco }}</p>
      }
    </div>
  `,
  styles: `
    .grid-toolbar {
      display: flex;
      flex-wrap: wrap;
      align-items: center;
      gap: 14px;
      margin-bottom: 20px;
    }
    .toolbar-spacer {
      flex: 1 1 auto;
    }
    .per-row-label {
      font-size: 0.7rem;
      letter-spacing: 0.08em;
      color: var(--mat-sys-on-surface-variant);
    }
    .watched-counter {
      font-size: 0.75rem;
      letter-spacing: 0.04em;
      color: var(--mat-sys-on-surface-variant);
      white-space: nowrap;
    }

    .tile-grid {
      display: grid;
      gap: 18px;
      grid-template-columns: repeat(var(--tiles-per-row, 6), 1fr);
    }
    @media (max-width: 640px) {
      .tile-grid {
        grid-template-columns: repeat(3, 1fr);
      }
    }
    @media (max-width: 420px) {
      .tile-grid {
        grid-template-columns: repeat(2, 1fr);
      }
    }
  `,
})
export class TitleGrid {
  readonly entries = input.required<TileEntry[]>();
  readonly recentlyChangedId = input<ImdbId | null>(null);
  readonly seenToggle = output<{ imdbId: ImdbId; seen: boolean }>();

  protected readonly userPrefs = inject(UserPrefsStore);
  protected readonly tileCountOptions = TILE_COUNT_OPTIONS;

  protected readonly sort = signal<Sort>({ active: '', direction: '' });
  protected readonly sorted = computed(() => sortRows(this.entries(), this.sort()));
  protected readonly watchedCount = computed(() => this.entries().filter((e) => e.isRated).length);
  protected readonly total = computed(() => this.entries().length);

  protected setSortField(active: string): void {
    this.sort.update((s) => ({ active, direction: s.direction || 'asc' }));
  }

  protected toggleDirection(): void {
    this.sort.update((s) => ({ ...s, direction: s.direction === 'asc' ? 'desc' : 'asc' }));
  }
}
