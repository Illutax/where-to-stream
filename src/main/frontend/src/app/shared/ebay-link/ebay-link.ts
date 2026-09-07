import { ChangeDetectionStrategy, Component, computed, inject, input } from '@angular/core';
import { TranslocoPipe } from '@jsverse/transloco';
import { ImdbId, ReleaseYear } from '../../core/domain';
import { injectEbaySearchUrl } from '../../core/ebay-search';
import { injectTitleMeta } from '../../core/title-meta';
import { UserPrefsStore } from '../../core/user-prefs-store';

/** How the link presents itself — a word in a table cell, or the eBay mark under a poster. */
export type EbayLinkAppearance = 'text' | 'badge';

/**
 * The eBay search link for one title (TODO-57): a word in the table's eBay column, or the eBay
 * wordmark as a badge under a poster tile.
 *
 * <p>One component for both so the two dashboard views cannot drift apart — they must open the
 * same search, or the same row means different things depending on the view mode. Everything that
 * decides *what* is searched lives in {@link injectEbaySearchUrl}; this only decides how it looks.
 *
 * <p>Renders nothing at all when there is no sensible search to offer (an unreleased title, a row
 * without a machine-readable year).
 */
@Component({
  selector: 'app-ebay-link',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [TranslocoPipe],
  template: `
    @if (url(); as href) {
      <a
        class="ebay-link"
        [class.ebay-link--badge]="appearance() === 'badge'"
        [href]="href"
        target="_blank"
        rel="noopener"
        [attr.aria-label]="'ebay.searchFor' | transloco: { name: searchedTitle() }">
        @if (appearance() === 'badge') {
          <!--
            The wordmark, not translatable copy — hence aria-hidden with the accessible name on the
            anchor. Written out letter by letter because the four colours are the recognisable part;
            an image would be another asset to ship and to theme.
          -->
          <span class="wordmark" aria-hidden="true"
            ><span class="e">e</span><span class="b">b</span><span class="a">a</span><span class="y">y</span></span
          >
        } @else {
          {{ 'ebay.search' | transloco }}
        }
      </a>
    }
  `,
  styles: `
    .ebay-link {
      display: inline-flex;
      align-items: center;
      min-height: 24px;
      padding: 0 0.35rem;
      font-size: 0.78rem;
      white-space: nowrap;
      color: var(--mat-sys-primary);
    }
    /* WCAG 2.5.8 wants 24px; a coarse pointer gets the 44px the watched toggle already uses. */
    @media (pointer: coarse) {
      .ebay-link {
        min-height: 44px;
      }
    }

    /*
     * The badge keeps a light chip in both themes and fixed brand colours, for the same reason the
     * poster chrome above it does: a logo that changes colour with the theme stops being the logo.
     * Contrast is not a WCAG question here either — 1.4.3 exempts logotypes — but the light chip is
     * what makes the yellow and the green legible at all.
     */
    .ebay-link--badge {
      padding: 0.25rem 0.35rem;
    }
    .wordmark {
      padding: 2px 7px;
      border-radius: 3px;
      background: #fdfdfd;
      box-shadow: inset 0 0 0 1px rgba(16, 14, 12, 0.16);
      font: 700 12px/1.2 inherit;
      letter-spacing: -0.02em;
    }
    .wordmark .e {
      color: #e53238;
    }
    .wordmark .b {
      color: #0064d2;
    }
    .wordmark .a {
      color: #f5af02;
    }
    .wordmark .y {
      color: #86b817;
    }
  `,
})
export class EbayLink {
  readonly imdbId = input.required<ImdbId>();
  /** The title as the server delivered it. */
  readonly name = input<string>('');
  /** Null where the row carries no machine-readable year — then there is no link. */
  readonly releaseYear = input<ReleaseYear | null>(null);
  readonly appearance = input<EbayLinkAppearance>('text');

  private readonly userPrefs = inject(UserPrefsStore);
  private readonly meta = injectTitleMeta(() => this.imdbId());

  protected readonly url = injectEbaySearchUrl({
    name: this.name,
    year: this.releaseYear,
    germanTitle: () => this.meta()?.germanTitle ?? null,
  });

  /**
   * The title the accessible name announces — the one that is actually searched for, and the one
   * the row displays. Three different names for the same film would defeat the point of naming it.
   */
  protected readonly searchedTitle = computed(
    () => (this.userPrefs.showGermanTitle() && this.meta()?.germanTitle) || this.name(),
  );
}
