import { ChangeDetectionStrategy, Component, computed, input, output, signal } from '@angular/core';
import { MatSortModule, Sort } from '@angular/material/sort';
import { MatTableModule } from '@angular/material/table';
import { ImdbId, imdbId, releaseYear, releaseYearDisplay, watchlistDate } from '../../core/domain';
import { OverviewEntry } from '../../core/models';
import { TitleCell } from '../title-cell/title-cell';
import { TranslocoPipe } from '@jsverse/transloco';
import { sortRows } from '../sort/table-sort';

/** Placeholder rows shown while loading (see {@link CatalogTable.loading}) — never rendered as real data. */
const SKELETON_ROWS: OverviewEntry[] = Array.from({ length: 8 }, (_, i) => ({
  imdbId: imdbId(`tt_skeleton_${i}`),
  name: '',
  year: releaseYear(0),
  added: watchlistDate('1970-01-01'),
  isRated: false,
  services: null,
}));

/** Presentational catalogue table: every title with the services it streams on. */
@Component({
  selector: 'app-catalog-table',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [MatTableModule, MatSortModule, TitleCell, TranslocoPipe],
  template: `
    <div class="table-scroll">
    <table mat-table [dataSource]="sorted()" [trackBy]="trackByImdbId"
           matSort (matSortChange)="sort.set($event)">
      <ng-container matColumnDef="rated">
        <th mat-header-cell *matHeaderCellDef mat-sort-header>{{ 'table.seen' | transloco }}</th>
        <td mat-cell *matCellDef="let entry">
          @if (loading()) {
            <span class="skeleton-bar skeleton-rated"></span>
          } @else {
            <button type="button" class="seen-toggle"
                    (click)="seenToggle.emit({ imdbId: entry.imdbId, seen: !entry.isRated })"
                    [attr.aria-label]="(entry.isRated ? 'table.markNotSeen' : 'table.markSeen') | transloco: { name: entry.name }"
                    [attr.aria-pressed]="entry.isRated">{{ entry.isRated ? '✅' : '⭕' }}</button>
          }
        </td>
      </ng-container>

      <ng-container matColumnDef="title">
        <th mat-header-cell *matHeaderCellDef mat-sort-header>{{ 'table.title' | transloco }}</th>
        <td mat-cell *matCellDef="let entry">
          @if (loading()) {
            <span class="skeleton-bar"></span>
          } @else {
            <app-title-cell [imdbId]="entry.imdbId" [name]="entry.name" />
          }
        </td>
      </ng-container>

      <ng-container matColumnDef="year">
        <th mat-header-cell *matHeaderCellDef mat-sort-header>{{ 'table.year' | transloco }}</th>
        <td mat-cell *matCellDef="let entry">
          @if (loading()) {
            <span class="skeleton-bar skeleton-bar--narrow"></span>
          } @else {
            {{ releaseYearDisplay(entry.year) }}
          }
        </td>
      </ng-container>

      <ng-container matColumnDef="added">
        <th mat-header-cell *matHeaderCellDef mat-sort-header>{{ 'table.added' | transloco }}</th>
        <td mat-cell *matCellDef="let entry">
          @if (loading()) {
            <span class="skeleton-bar skeleton-bar--narrow"></span>
          } @else {
            {{ entry.added }}
          }
        </td>
      </ng-container>

      <ng-container matColumnDef="services">
        <th mat-header-cell *matHeaderCellDef>{{ 'table.availableOn' | transloco }}</th>
        <td mat-cell *matCellDef="let entry">
          @if (loading()) {
            <span class="skeleton-bar"></span>
          } @else if (entry.services) { {{ entry.services }} } @else { <em class="text-muted">{{ 'table.na' | transloco }}</em> }
        </td>
      </ng-container>

      <tr mat-header-row *matHeaderRowDef="displayedColumns"></tr>
      <tr mat-row *matRowDef="let row; columns: displayedColumns"
          [class.recently-changed]="row.imdbId === recentlyChangedId()"></tr>
      <tr class="mat-row" *matNoDataRow>
        <td class="mat-cell text-muted" [attr.colspan]="displayedColumns.length">{{ 'table.noEntries' | transloco }}</td>
      </tr>
    </table>
    </div>
  `,
})
export class CatalogTable {
  readonly entries = input.required<OverviewEntry[]>();
  readonly recentlyChangedId = input<ImdbId | null>(null);
  /** While true, renders placeholder rows instead of {@link entries} (still loading). */
  readonly loading = input(false);
  readonly seenToggle = output<{ imdbId: ImdbId; seen: boolean }>();
  protected readonly sort = signal<Sort>({ active: '', direction: '' });
  protected readonly sorted = computed(() => (this.loading() ? SKELETON_ROWS : sortRows(this.entries(), this.sort())));
  protected readonly displayedColumns = ['rated', 'title', 'year', 'added', 'services'];
  protected readonly trackByImdbId = (_: number, entry: OverviewEntry) => entry.imdbId;
  protected readonly releaseYearDisplay = releaseYearDisplay;
}
