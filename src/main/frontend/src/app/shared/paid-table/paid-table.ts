import { ChangeDetectionStrategy, Component, input } from '@angular/core';
import { MatTableModule } from '@angular/material/table';
import { PaidEntry } from '../../core/models';

/** Presentational table of purchasable/rentable ("kaufbar") titles for a provider. */
@Component({
  selector: 'app-paid-table',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [MatTableModule],
  template: `
    <table mat-table [dataSource]="entries()" [trackBy]="trackByRow">
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

      <ng-container matColumnDef="price">
        <th mat-header-cell *matHeaderCellDef>Price</th>
        <td mat-cell *matCellDef="let entry">{{ entry.price }}</td>
      </ng-container>

      <ng-container matColumnDef="languages">
        <th mat-header-cell *matHeaderCellDef>Languages</th>
        <td mat-cell *matCellDef="let entry">@if (entry.languages) { {{ entry.languages }} }</td>
      </ng-container>

      <ng-container matColumnDef="year">
        <th mat-header-cell *matHeaderCellDef>Year</th>
        <td mat-cell *matCellDef="let entry">{{ entry.year }}</td>
      </ng-container>

      <ng-container matColumnDef="added">
        <th mat-header-cell *matHeaderCellDef>Added</th>
        <td mat-cell *matCellDef="let entry">{{ entry.added }}</td>
      </ng-container>

      <tr mat-header-row *matHeaderRowDef="displayedColumns"></tr>
      <tr mat-row *matRowDef="let row; columns: displayedColumns"></tr>
      <tr class="mat-row" *matNoDataRow>
        <td class="mat-cell text-muted" [attr.colspan]="displayedColumns.length">Nothing here.</td>
      </tr>
    </table>
  `,
})
export class PaidTable {
  readonly entries = input.required<PaidEntry[]>();
  protected readonly displayedColumns = ['rated', 'title', 'price', 'languages', 'year', 'added'];
  protected readonly trackByRow = (_: number, entry: PaidEntry) => entry.imdbId + entry.languages;
}
