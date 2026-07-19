import { ChangeDetectionStrategy, Component, input } from '@angular/core';
import { MatTableModule } from '@angular/material/table';
import { OverviewEntry } from '../../core/models';

/** Presentational catalogue table: every title with the services it streams on. */
@Component({
  selector: 'app-catalog-table',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [MatTableModule],
  template: `
    <table mat-table [dataSource]="entries()" [trackBy]="trackByImdbId">
      <ng-container matColumnDef="rated">
        <th mat-header-cell *matHeaderCellDef></th>
        <td mat-cell *matCellDef="let entry">@if (entry.isRated) { <span title="Seen">✅</span> }</td>
      </ng-container>

      <ng-container matColumnDef="title">
        <th mat-header-cell *matHeaderCellDef>Title</th>
        <td mat-cell *matCellDef="let entry">
          <a [href]="'https://www.imdb.com/title/' + entry.imdbId" target="_blank" rel="noopener">{{ entry.name }}</a>
        </td>
      </ng-container>

      <ng-container matColumnDef="year">
        <th mat-header-cell *matHeaderCellDef>Year</th>
        <td mat-cell *matCellDef="let entry">{{ entry.year }}</td>
      </ng-container>

      <ng-container matColumnDef="added">
        <th mat-header-cell *matHeaderCellDef>Added</th>
        <td mat-cell *matCellDef="let entry">{{ entry.added }}</td>
      </ng-container>

      <ng-container matColumnDef="services">
        <th mat-header-cell *matHeaderCellDef>Available on</th>
        <td mat-cell *matCellDef="let entry">
          @if (entry.services) { {{ entry.services }} } @else { <em class="text-muted">N/A</em> }
        </td>
      </ng-container>

      <tr mat-header-row *matHeaderRowDef="displayedColumns"></tr>
      <tr mat-row *matRowDef="let row; columns: displayedColumns"></tr>
      <tr class="mat-row" *matNoDataRow>
        <td class="mat-cell text-muted" [attr.colspan]="displayedColumns.length">No entries.</td>
      </tr>
    </table>
  `,
})
export class CatalogTable {
  readonly entries = input.required<OverviewEntry[]>();
  protected readonly displayedColumns = ['rated', 'title', 'year', 'added', 'services'];
  protected readonly trackByImdbId = (_: number, entry: OverviewEntry) => entry.imdbId;
}
