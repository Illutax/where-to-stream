import { ChangeDetectionStrategy, Component, input } from '@angular/core';
import { OverviewEntry } from '../../core/models';

/** Presentational catalogue table: every title with the services it streams on. */
@Component({
  selector: 'app-catalog-table',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <table class="table table-sm table-hover align-middle">
      <thead>
        <tr>
          <th scope="col"></th>
          <th scope="col">Title</th>
          <th scope="col">Year</th>
          <th scope="col">Added</th>
          <th scope="col">Available on</th>
        </tr>
      </thead>
      <tbody>
        @for (entry of entries(); track entry.imdbId) {
          <tr>
            <td>@if (entry.isRated) { <span title="Seen">✅</span> }</td>
            <td>
              <a [href]="'https://www.imdb.com/title/' + entry.imdbId" target="_blank" rel="noopener">
                {{ entry.name }}
              </a>
            </td>
            <td>{{ entry.year }}</td>
            <td>{{ entry.added }}</td>
            <td>
              @if (entry.services) { {{ entry.services }} }
              @else { <em class="text-secondary">N/A</em> }
            </td>
          </tr>
        } @empty {
          <tr><td colspan="5" class="text-secondary">No entries.</td></tr>
        }
      </tbody>
    </table>
  `,
})
export class CatalogTable {
  readonly entries = input.required<OverviewEntry[]>();
}
