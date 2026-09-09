import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';
import { MatCardModule } from '@angular/material/card';

/**
 * One figure on the metrics dashboard: a big number, a label, and optionally the part of it that
 * is not what the label suggests.
 *
 * <p>That last part is the reason this is a component rather than a `<p>`. Several of these counts
 * mean the opposite of what they look like unless the qualifier travels with them — "1,200 posters"
 * reads as a full cache when 900 of the rows record "this title has no poster". So `detail` sits in
 * the tile, not in a footnote somebody has to find.
 */
@Component({
  selector: 'app-metric-tile',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [MatCardModule],
  template: `
    <mat-card class="metric-tile" appearance="outlined">
      <mat-card-content>
        @if (loading()) {
          <span class="skeleton-bar skeleton-bar--narrow"></span>
          <span class="skeleton-bar"></span>
        } @else {
          <p class="metric-value">{{ formatted() }}</p>
          <p class="metric-label">{{ label() }}</p>
          @if (detail()) {
            <p class="metric-detail">{{ detail() }}</p>
          }
        }
      </mat-card-content>
    </mat-card>
  `,
  styles: `
    .metric-tile {
      height: 100%;
    }
    .metric-value {
      font: var(--mat-sys-display-small);
      margin: 0;
      /* Tabular figures so a column of tiles does not shift as the numbers change width. */
      font-variant-numeric: tabular-nums;
      overflow-wrap: anywhere;
    }
    .metric-label {
      font: var(--mat-sys-body-medium);
      color: var(--mat-sys-on-surface-variant);
      margin: 0.25rem 0 0;
    }
    .metric-detail {
      font: var(--mat-sys-body-small);
      color: var(--mat-sys-on-surface-variant);
      margin: 0.5rem 0 0;
    }
  `,
})
export class MetricTile {
  readonly label = input.required<string>();
  readonly value = input<number | null>(null);
  /** A qualifier shown under the number — e.g. how much of it is a negative-cache entry. */
  readonly detail = input<string | null>(null);
  /** While true, renders placeholder bars instead of the number. */
  readonly loading = input(false);

  /** Grouped digits, in the active locale, so six-figure counts stay readable. */
  protected readonly formatted = computed(() => (this.value() ?? 0).toLocaleString());
}
