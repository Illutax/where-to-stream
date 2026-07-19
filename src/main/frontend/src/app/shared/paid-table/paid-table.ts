import { ChangeDetectionStrategy, Component, input } from '@angular/core';
import { PaidEntry } from '../../core/models';

/** Presentational table of purchasable/rentable ("kaufbar") titles for a provider. */
@Component({
  selector: 'app-paid-table',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <table class="table table-sm table-hover align-middle">
      <thead>
        <tr>
          <th scope="col"></th>
          <th scope="col">Title</th>
          <th scope="col">Price</th>
          <th scope="col">Languages</th>
          <th scope="col">Year</th>
          <th scope="col">Added</th>
        </tr>
      </thead>
      <tbody>
        @for (entry of entries(); track entry.imdbId + entry.languages) {
          <tr>
            <td>@if (entry.isRated) { <span title="Seen">✅</span> }</td>
            <td>
              <a [href]="'https://www.imdb.com/title/' + entry.imdbId" target="_blank" rel="noopener">
                {{ entry.name }}
              </a>
            </td>
            <td>{{ entry.price }}</td>
            <td>@if (entry.languages) { {{ entry.languages }} }</td>
            <td>{{ entry.year }}</td>
            <td>{{ entry.added }}</td>
          </tr>
        } @empty {
          <tr><td colspan="6" class="text-secondary">Nothing here.</td></tr>
        }
      </tbody>
    </table>
  `,
})
export class PaidTable {
  readonly entries = input.required<PaidEntry[]>();
}
