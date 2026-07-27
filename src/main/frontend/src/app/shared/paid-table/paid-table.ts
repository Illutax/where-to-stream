import { ChangeDetectionStrategy, Component, computed, input, output, signal } from '@angular/core';
import { MatSortModule, Sort } from '@angular/material/sort';
import { MatTableModule } from '@angular/material/table';
import { ImdbId, imdbUrl } from '../../core/domain';
import { PaidEntry } from '../../core/models';
import { AgeBadge } from '../age-badge/age-badge';
import { PosterThumb } from '../poster-thumb/poster-thumb';
import { sortRows } from '../sort/table-sort';

/** Presentational table of purchasable/rentable ("kaufbar") titles for a provider. */
@Component({
  selector: 'app-paid-table',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [MatTableModule, MatSortModule, PosterThumb, AgeBadge],
  template: `
    <div class="table-scroll">
    <table mat-table [dataSource]="sorted()" [trackBy]="trackByRow"
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
          <div class="title-cell">
            <app-poster-thumb [imdbId]="entry.imdbId" [name]="entry.name" />
            <a [href]="imdbUrl(entry.imdbId)" target="_blank" rel="noopener">{{ entry.name }}</a>
            <app-age-badge [imdbId]="entry.imdbId" />
          </div>
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
      <tr mat-row *matRowDef="let row; columns: displayedColumns"
          [class.recently-changed]="row.imdbId === recentlyChangedId()"></tr>
      <tr class="mat-row" *matNoDataRow>
        <td class="mat-cell text-muted" [attr.colspan]="displayedColumns.length">Nothing here.</td>
      </tr>
    </table>
    </div>
  `,
})
export class PaidTable {
  readonly entries = input.required<PaidEntry[]>();
  readonly recentlyChangedId = input<ImdbId | null>(null);
  readonly seenToggle = output<{ imdbId: ImdbId; seen: boolean }>();
  protected readonly sort = signal<Sort>({ active: '', direction: '' });
  protected readonly sorted = computed(() => sortRows(this.entries(), this.sort()));
  protected readonly displayedColumns = ['rated', 'title', 'price', 'languages', 'year', 'added'];
  protected readonly trackByRow = (_: number, entry: PaidEntry) => entry.imdbId + entry.languages;
  protected readonly imdbUrl = imdbUrl;
}
