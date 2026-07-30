import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';
import { ViewMode } from '../../core/models';
import { TitleTileSkeleton } from '../title-tile-skeleton/title-tile-skeleton';

/** How many placeholder rows to show while loading — enough to fill an initial viewport. */
const GRID_SKELETON_ROWS = 3;
const LIST_SKELETON_ROWS = 8;

/**
 * Placeholder shown while /api/catalog is loading, shaped like whichever view mode is about to
 * render (a grid of poster-sized boxes, or a table of row bars) instead of a bare spinner —
 * so the page doesn't jump from a small loading indicator to a full page of content once the
 * real data arrives.
 */
@Component({
  selector: 'app-catalog-skeleton',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [TitleTileSkeleton],
  template: `
    @if (viewMode() === 'GRID') {
      <div class="tile-grid" [style.--tiles-per-row]="tilesPerRow()">
        @for (i of gridPlaceholders(); track i) {
          <app-title-tile-skeleton />
        }
      </div>
    } @else {
      <div class="table-skeleton">
        @for (i of listPlaceholders; track i) {
          <div class="row-skeleton">
            <span class="cell cell-rated"></span>
            <span class="cell cell-title"></span>
            <span class="cell cell-year"></span>
            <span class="cell cell-added"></span>
            <span class="cell cell-services"></span>
          </div>
        }
      </div>
    }
  `,
  styles: `
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

    .table-skeleton {
      display: flex;
      flex-direction: column;
    }
    .row-skeleton {
      display: flex;
      align-items: center;
      gap: 16px;
      padding: 12px 8px;
      border-bottom: 1px solid var(--mat-sys-outline-variant);
    }
    .cell {
      display: block;
      height: 14px;
      border-radius: 3px;
      background: var(--mat-sys-surface-variant);
    }
    .cell-rated {
      width: 20px;
      height: 20px;
      border-radius: 50%;
      flex: none;
    }
    .cell-title {
      flex: 2 1 auto;
    }
    .cell-year {
      flex: 0 1 50px;
    }
    .cell-added {
      flex: 0 1 80px;
    }
    .cell-services {
      flex: 1 1 auto;
    }
    @media (prefers-reduced-motion: no-preference) {
      .cell {
        animation: skeleton-pulse 1.4s ease-in-out infinite;
      }
    }
    @keyframes skeleton-pulse {
      0%,
      100% {
        opacity: 1;
      }
      50% {
        opacity: 0.55;
      }
    }
  `,
})
export class CatalogSkeleton {
  readonly viewMode = input.required<ViewMode>();
  readonly tilesPerRow = input.required<number>();

  protected readonly gridPlaceholders = computed(() =>
    Array.from({ length: this.tilesPerRow() * GRID_SKELETON_ROWS }, (_, i) => i),
  );
  protected readonly listPlaceholders = Array.from({ length: LIST_SKELETON_ROWS }, (_, i) => i);
}
