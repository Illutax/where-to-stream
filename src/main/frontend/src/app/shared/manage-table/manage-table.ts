import { ChangeDetectionStrategy, Component, input, output, signal } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatCheckboxModule } from '@angular/material/checkbox';
import { MatTableModule } from '@angular/material/table';
import { ManageRow } from '../../core/models';

/**
 * Presentational cache-management table. Holds only local selection (view) state; the actual
 * invalidate/scrape work is emitted to the smart parent, which calls the API.
 */
@Component({
  selector: 'app-manage-table',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [MatTableModule, MatCheckboxModule, MatButtonModule],
  template: `
    <form (submit)="onScrape($event)" class="scrape-form">
      <p>
        <span>{{ needsScrapeCount() }}</span> title(s) currently need scraping
        (never cached or invalidated).
      </p>
      <button matButton="filled" type="submit">Scrape invalidated / missing</button>
    </form>

    <h2>Invalidate selected titles</h2>
    <form (submit)="onInvalidate($event)">
      <table mat-table [dataSource]="rows()" [trackBy]="trackByImdbId">
        <ng-container matColumnDef="select">
          <th mat-header-cell *matHeaderCellDef></th>
          <td mat-cell *matCellDef="let row">
            <mat-checkbox [checked]="selected().has(row.imdbId)" (change)="toggle(row.imdbId)"
                          [aria-label]="'select ' + row.name" />
          </td>
        </ng-container>

        <ng-container matColumnDef="title">
          <th mat-header-cell *matHeaderCellDef>Title</th>
          <td mat-cell *matCellDef="let row">{{ row.name }}</td>
        </ng-container>

        <ng-container matColumnDef="imdbId">
          <th mat-header-cell *matHeaderCellDef>IMDb id</th>
          <td mat-cell *matCellDef="let row">{{ row.imdbId }}</td>
        </ng-container>

        <ng-container matColumnDef="status">
          <th mat-header-cell *matHeaderCellDef>Status</th>
          <td mat-cell *matCellDef="let row">
            @if (row.needsScrape) {
              <span class="status-pill status-pill--needs-scrape">needs scrape</span>
            } @else {
              <span class="status-pill status-pill--cached">cached</span>
            }
          </td>
        </ng-container>

        <tr mat-header-row *matHeaderRowDef="displayedColumns"></tr>
        <tr mat-row *matRowDef="let row; columns: displayedColumns"></tr>
        <tr class="mat-row" *matNoDataRow>
          <td class="mat-cell text-muted" [attr.colspan]="displayedColumns.length">No titles.</td>
        </tr>
      </table>

      <button matButton="filled" type="submit" class="invalidate-button" [disabled]="selected().size === 0">
        Invalidate selected
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

  readonly invalidate = output<string[]>();
  readonly scrape = output<void>();

  protected readonly displayedColumns = ['select', 'title', 'imdbId', 'status'];
  protected readonly trackByImdbId = (_: number, row: ManageRow) => row.imdbId;
  protected readonly selected = signal<ReadonlySet<string>>(new Set());

  protected toggle(imdbId: string): void {
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
