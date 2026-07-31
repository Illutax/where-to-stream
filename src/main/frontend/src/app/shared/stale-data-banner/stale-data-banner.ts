import { ChangeDetectionStrategy, Component, input } from '@angular/core';
import { TranslocoPipe } from '@jsverse/transloco';

/**
 * Unobtrusive, page-wide hint (not an error) shown when some of the displayed titles are
 * currently served from stale cache data while a background refresh is under way (ADR-0016).
 */
@Component({
  selector: 'app-stale-data-banner',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [TranslocoPipe],
  template: `
    @if (visible()) {
      <div class="stale-data-banner" role="status">{{ 'common.staleDataBanner' | transloco }}</div>
    }
  `,
})
export class StaleDataBanner {
  readonly visible = input(false);
}
