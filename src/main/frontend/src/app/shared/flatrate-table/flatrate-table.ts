import { ChangeDetectionStrategy, Component, computed, input, output, signal } from '@angular/core';
import { MatSortModule, Sort } from '@angular/material/sort';
import { MatTableModule } from '@angular/material/table';
import { ImdbId, releaseYearDisplay } from '../../core/domain';
import { FlatrateEntry } from '../../core/models';
import { TitleCell } from '../title-cell/title-cell';
import { sortRows } from '../sort/table-sort';

/** Presentational table of flatrate ("included") titles for a provider. */
@Component({
  selector: 'app-flatrate-table',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [MatTableModule, MatSortModule, TitleCell],
  template: `
    <div class="table-scroll">
    <table mat-table [dataSource]="sorted()" [trackBy]="trackByImdbId"
           matSort (matSortChange)="sort.set($event)">
      <ng-container matColumnDef="rated">
        <th mat-header-cell *matHeaderCellDef mat-sort-header>Seen</th>
        <td mat-cell *matCellDef="let entry">
          <button type="button" class="seen-toggle"
                  (click)="seenToggle.emit({ imdbId: entry.imdbId, seen: !entry.isRated })"
                  [attr.aria-label]="(entry.isRated ? 'Mark not seen: ' : 'Mark seen: ') + entry.name"
                  [attr.aria-pressed]="entry.isRated">{{ entry.isRated ? '✅' : '⭕' }}</button>
        </td>
      </ng-container>

      <ng-container matColumnDef="title">
        <th mat-header-cell *matHeaderCellDef mat-sort-header>Title</th>
        <td mat-cell *matCellDef="let entry">
          <app-title-cell [imdbId]="entry.imdbId" [name]="entry.name" />
        </td>
      </ng-container>

      <ng-container matColumnDef="year">
        <th mat-header-cell *matHeaderCellDef mat-sort-header>Year</th>
        <td mat-cell *matCellDef="let entry">{{ releaseYearDisplay(entry.year) }}</td>
      </ng-container>

      <ng-container matColumnDef="added">
        <th mat-header-cell *matHeaderCellDef mat-sort-header>Added</th>
        <td mat-cell *matCellDef="let entry">{{ entry.added }}</td>
      </ng-container>

      <tr mat-header-row *matHeaderRowDef="displayedColumns"></tr>
      <tr mat-row *matRowDef="let row; columns: displayedColumns"
          [class.recently-changed]="row.imdbId === recentlyChangedId()"></tr>
      <tr class="mat-row" *matNoDataRow>
        <td class="mat-cell text-muted" [attr.colspan]="displayedColumns.length">Nothing here.</td>
      </tr>
    </table>
    </div>
  `,
})
export class FlatrateTable {
  readonly entries = input.required<FlatrateEntry[]>();
  readonly recentlyChangedId = input<ImdbId | null>(null);
  readonly seenToggle = output<{ imdbId: ImdbId; seen: boolean }>();
  protected readonly sort = signal<Sort>({ active: '', direction: '' });
  protected readonly sorted = computed(() => sortRows(this.entries(), this.sort()));
  protected readonly displayedColumns = ['rated', 'title', 'year', 'added'];
  protected readonly trackByImdbId = (_: number, entry: FlatrateEntry) => entry.imdbId;
  protected readonly releaseYearDisplay = releaseYearDisplay;
}
