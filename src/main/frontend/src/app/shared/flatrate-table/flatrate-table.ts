import { ChangeDetectionStrategy, Component, input } from '@angular/core';
import { FlatrateEntry } from '../../core/models';

/** Presentational table of flatrate ("included") titles for a provider. */
@Component({
  selector: 'app-flatrate-table',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <table class="table table-sm table-hover align-middle">
      <thead>
        <tr>
          <th scope="col"></th>
          <th scope="col">Title</th>
          <th scope="col">Year</th>
          <th scope="col">Added</th>
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
          </tr>
        } @empty {
          <tr><td colspan="4" class="text-secondary">Nothing here.</td></tr>
        }
      </tbody>
    </table>
  `,
})
export class FlatrateTable {
  readonly entries = input.required<FlatrateEntry[]>();
}
