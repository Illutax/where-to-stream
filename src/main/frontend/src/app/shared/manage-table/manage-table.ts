import { ChangeDetectionStrategy, Component, input, output, signal } from '@angular/core';
import { ManageRow } from '../../core/models';

/**
 * Presentational cache-management table. Holds only local selection (view) state; the actual
 * invalidate/scrape work is emitted to the smart parent, which calls the API.
 */
@Component({
  selector: 'app-manage-table',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <form (submit)="onScrape($event)" class="mb-3">
      <p>
        <span>{{ needsScrapeCount() }}</span> title(s) currently need scraping
        (never cached or invalidated).
      </p>
      <button type="submit" class="btn btn-primary">Scrape invalidated / missing</button>
    </form>

    <hr />

    <h2 class="h5">Invalidate selected titles</h2>
    <form (submit)="onInvalidate($event)">
      <table class="table table-sm align-middle">
        <thead>
          <tr>
            <th scope="col"></th>
            <th scope="col">Title</th>
            <th scope="col">IMDb id</th>
            <th scope="col">Status</th>
          </tr>
        </thead>
        <tbody>
          @for (row of rows(); track row.imdbId) {
            <tr>
              <td>
                <input type="checkbox" [checked]="selected().has(row.imdbId)"
                       (change)="toggle(row.imdbId)" [attr.aria-label]="'select ' + row.name" />
              </td>
              <td>{{ row.name }}</td>
              <td>{{ row.imdbId }}</td>
              <td>
                @if (row.needsScrape) {
                  <span class="badge bg-warning text-dark">needs scrape</span>
                } @else {
                  <span class="badge bg-success">cached</span>
                }
              </td>
            </tr>
          } @empty {
            <tr><td colspan="4" class="text-secondary">No titles.</td></tr>
          }
        </tbody>
      </table>
      <button type="submit" class="btn btn-danger" [disabled]="selected().size === 0">
        Invalidate selected
      </button>
    </form>
  `,
})
export class ManageTable {
  readonly rows = input.required<ManageRow[]>();
  readonly needsScrapeCount = input.required<number>();

  readonly invalidate = output<string[]>();
  readonly scrape = output<void>();

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
