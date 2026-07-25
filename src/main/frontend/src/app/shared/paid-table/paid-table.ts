import { ChangeDetectionStrategy, Component, computed, input, signal } from '@angular/core';
import { MatSortModule, Sort } from '@angular/material/sort';
import { MatTableModule } from '@angular/material/table';
import { PaidEntry } from '../../core/models';
import { sortRows } from '../sort/table-sort';

/** Presentational table of purchasable/rentable ("kaufbar") titles for a provider. */
@Component({
  selector: 'app-paid-table',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [MatTableModule, MatSortModule],
  template: `
    <div class="table-scroll">
    <table mat-table [dataSource]="sorted()" [trackBy]="trackByRow"
           matSort (matSortChange)="sort.set($event)">
      <ng-container matColumnDef="rated">
        <th mat-header-cell *matHeaderCellDef></th>
        <td mat-cell *matCellDef="let entry">@if (entry.isRated) { <span title="Seen">✅</span> }</td>
      </ng-container>

      <ng-container matColumnDef="title">
        <th mat-header-cell *matHeaderCellDef mat-sort-header>Title</th>
        <td mat-cell *matCellDef="let entry">
          <a [href]="'https://www.imdb.com/title/' + entry.imdbId" target="_blank" rel="noopener">{{ entry.name }}</a>
        </td>
      </ng-container>

      <ng-container matColumnDef="price">
        <th mat-header-cell *matHeaderCellDef>Price</th>
        <td mat-cell *matCellDef="let entry">{{ entry.price }}</td>
      </ng-container>

      <ng-container matColumnDef="languages">
        <th mat-header-cell *matHeaderCellDef>Languages</th>
        <td mat-cell *matCellDef="let entry">@if (entry.languages) { {{ entry.languages }} }</td>
      </ng-container>

      <ng-container matColumnDef="year">
        <th mat-header-cell *matHeaderCellDef mat-sort-header>Year</th>
        <td mat-cell *matCellDef="let entry">{{ entry.year }}</td>
      </ng-container>

      <ng-container matColumnDef="added">
        <th mat-header-cell *matHeaderCellDef mat-sort-header>Added</th>
        <td mat-cell *matCellDef="let entry">{{ entry.added }}</td>
      </ng-container>

      <tr mat-header-row *matHeaderRowDef="displayedColumns"></tr>
      <tr mat-row *matRowDef="let row; columns: displayedColumns"></tr>
      <tr class="mat-row" *matNoDataRow>
        <td class="mat-cell text-muted" [attr.colspan]="displayedColumns.length">Nothing here.</td>
      </tr>
    </table>
    </div>
  `,
})
export class PaidTable {
  readonly entries = input.required<PaidEntry[]>();
  protected readonly sort = signal<Sort>({ active: '', direction: '' });
  protected readonly sorted = computed(() => sortRows(this.entries(), this.sort()));
  protected readonly displayedColumns = ['rated', 'title', 'price', 'languages', 'year', 'added'];
  protected readonly trackByRow = (_: number, entry: PaidEntry) => entry.imdbId + entry.languages;
}
