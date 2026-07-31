import { ChangeDetectionStrategy, Component, computed, input, output, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { MatButtonModule } from '@angular/material/button';
import { MatCheckboxModule } from '@angular/material/checkbox';
import { MatSortModule, Sort } from '@angular/material/sort';
import { MatTableModule } from '@angular/material/table';
import { TranslocoPipe } from '@jsverse/transloco';
import { ImdbId, imdbId } from '../../core/domain';
import { ManageRow } from '../../core/models';
import { sortManageRows } from '../sort/table-sort';

/** Placeholder rows shown while loading (see {@link ManageTable.loading}) — never rendered as real data. */
const SKELETON_ROWS: ManageRow[] = Array.from({ length: 6 }, (_, i) => ({
  imdbId: imdbId(`tt_skeleton_${i}`),
  name: '',
  isRated: false,
  needsScrape: false,
  lastScrapedAt: null,
}));

/**
 * Presentational cache-management table.
 * Holds only local selection (view) state;
 * the actual invalidate/scrape work is emitted to the smart parent, which calls the API.
 */
@Component({
  selector: 'app-manage-table',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [MatTableModule, MatCheckboxModule, MatButtonModule, MatSortModule, TranslocoPipe, DatePipe],
  template: `
    <form (submit)="onScrape($event)" class="scrape-form">
      <p>
        @if (loading()) {
          <span class="skeleton-bar skeleton-bar--narrow"></span>
        } @else {
          <span>{{ needsScrapeCount() }}</span> {{ 'manage.needScraping' | transloco }}
        }
      </p>
      <button matButton="filled" type="submit" [disabled]="loading()">{{ 'manage.scrapeButton' | transloco }}</button>
    </form>

    <h2>{{ 'manage.invalidateHeading' | transloco }}</h2>
    <form (submit)="onInvalidate($event)">
      <div class="table-scroll">
      <table mat-table [dataSource]="sorted()" [trackBy]="trackByImdbId"
             matSort (matSortChange)="sort.set($event)">
        <ng-container matColumnDef="select">
          <th mat-header-cell *matHeaderCellDef></th>
          <td mat-cell *matCellDef="let row">
            @if (loading()) {
              <span class="skeleton-bar skeleton-rated"></span>
            } @else {
              <mat-checkbox [checked]="selected().has(row.imdbId)" (change)="toggle(row.imdbId)"
                            [aria-label]="'manage.select' | transloco: { name: row.name }" />
            }
          </td>
        </ng-container>

        <ng-container matColumnDef="title">
          <th mat-header-cell *matHeaderCellDef mat-sort-header>{{ 'manage.columnTitle' | transloco }}</th>
          <td mat-cell *matCellDef="let row">
            @if (loading()) {
              <span class="skeleton-bar"></span>
            } @else {
              {{ row.name }}
            }
          </td>
        </ng-container>

        <ng-container matColumnDef="imdbId">
          <th mat-header-cell *matHeaderCellDef>{{ 'manage.columnImdbId' | transloco }}</th>
          <td mat-cell *matCellDef="let row">
            @if (loading()) {
              <span class="skeleton-bar skeleton-bar--narrow"></span>
            } @else {
              {{ row.imdbId }}
            }
          </td>
        </ng-container>

        <ng-container matColumnDef="status">
          <th mat-header-cell *matHeaderCellDef mat-sort-header="lastScrapedAt">{{ 'manage.columnStatus' | transloco }}</th>
          <td mat-cell *matCellDef="let row">
            @if (loading()) {
              <span class="skeleton-bar skeleton-bar--narrow"></span>
            } @else if (row.needsScrape) {
              <span class="status-pill status-pill--needs-scrape">{{ 'manage.statusNeedsScrape' | transloco }}</span>
            } @else {
              {{ row.lastScrapedAt | date: 'short' }}
            }
          </td>
        </ng-container>

        <tr mat-header-row *matHeaderRowDef="displayedColumns"></tr>
        <tr mat-row *matRowDef="let row; columns: displayedColumns"></tr>
        <tr class="mat-row" *matNoDataRow>
          <td class="mat-cell text-muted" [attr.colspan]="displayedColumns.length">{{ 'manage.noTitles' | transloco }}</td>
        </tr>
      </table>
      </div>

      <button matButton="filled" type="submit" class="invalidate-button" [disabled]="loading() || selected().size === 0">
        {{ 'manage.invalidateSelected' | transloco }}
      </button>
    </form>
  `,
  styles: `
    .scrape-form {
      margin-bottom: 1rem;
    }
    .invalidate-button {
      margin-top: 1rem;
    }
    table {
      width: 100%;
    }
  `,
})
export class ManageTable {
  readonly rows = input.required<ManageRow[]>();
  readonly needsScrapeCount = input.required<number>();
  /** While true, renders placeholder rows instead of {@link rows} (still loading). */
  readonly loading = input(false);

  readonly invalidate = output<ImdbId[]>();
  readonly scrape = output<void>();

  protected readonly displayedColumns = ['select', 'title', 'imdbId', 'status'];
  protected readonly trackByImdbId = (_: number, row: ManageRow) => row.imdbId;
  protected readonly selected = signal<ReadonlySet<ImdbId>>(new Set());
  protected readonly sort = signal<Sort>({ active: '', direction: '' });
  protected readonly sorted = computed(() => (this.loading() ? SKELETON_ROWS : sortManageRows(this.rows(), this.sort())));

  protected toggle(imdbId: ImdbId): void {
    const next = new Set(this.selected());
    if (next.has(imdbId)) {
      next.delete(imdbId);
    } else {
      next.add(imdbId);
    }
    this.selected.set(next);
  }

  protected onInvalidate(event: Event): void {
    event.preventDefault();
    if (this.selected().size > 0) {
      this.invalidate.emit([...this.selected()]);
      this.selected.set(new Set());
    }
  }

  protected onScrape(event: Event): void {
    event.preventDefault();
    this.scrape.emit();
  }
}
