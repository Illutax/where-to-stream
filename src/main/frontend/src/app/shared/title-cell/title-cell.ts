import { ChangeDetectionStrategy, Component, computed, inject, input } from '@angular/core';
import { TranslocoPipe } from '@jsverse/transloco';
import { ImdbId, imdbUrl, releaseYear, ReleaseYear } from '../../core/domain';
import { ebaySearchUrl } from '../../core/ebay-search';
import { injectTitleMeta } from '../../core/title-meta';
import { UserPrefsStore } from '../../core/user-prefs-store';
import { AgeBadge } from '../age-badge/age-badge';
import { PosterThumb } from '../poster-thumb/poster-thumb';

/**
 * A table title cell: the poster thumbnail, the title link (in German when the user's German-title
 * preference is on and a German title exists, otherwise the original name), and the FSK age badge.
 * The per-title metadata (rating + German title) is fetched **once** from
 * {@code /api/titles/{id}/meta} — and only while at least one of those two preferences is on,
 * so it costs nothing when both are off.
 * On the dashboard the cell also carries the eBay search link (TODO-57).
 */
@Component({
  selector: 'app-title-cell',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [PosterThumb, AgeBadge, TranslocoPipe],
  template: `
    <div class="title-cell">
      <app-poster-thumb [imdbId]="imdbId()" [name]="name()" />
      <a [href]="imdbUrl(imdbId())" target="_blank" rel="noopener">{{ displayTitle() }}</a>
      @if (userPrefsStore.showAgeRatings() && meta()?.rating; as rating) {
        <app-age-badge [rating]="rating" />
      }
      @if (ebayUrl(); as url) {
        <a class="ebay-link" [href]="url" target="_blank" rel="noopener"
           [attr.aria-label]="'ebay.searchFor' | transloco: { name: name() }">{{ 'ebay.link' | transloco }}</a>
      }
    </div>
  `,
  styles: `
    .ebay-link {
      font-size: 0.78rem;
      white-space: nowrap;
      color: var(--mat-sys-secondary);
    }
  `,
})
export class TitleCell {
  readonly imdbId = input.required<ImdbId>();
  readonly name = input<string>('');
  /**
   * Whether to offer the eBay search link. Off by default: {@link TitleCell} is shared with the
   * provider pages, where a jump to a shop has no business being (TODO-57).
   */
  readonly showEbayLink = input(false);
  /** Only for the eBay link — an unreleased title (0) gets none. */
  readonly year = input<ReleaseYear>(releaseYear(0));

  protected readonly userPrefsStore = inject(UserPrefsStore);
  protected readonly imdbUrl = imdbUrl;
  protected readonly meta = injectTitleMeta(() => this.imdbId());

  /** The German title when the preference is on and one exists, else the original (English) name. */
  protected readonly displayTitle = computed(
    () => (this.userPrefsStore.showGermanTitle() && this.meta()?.germanTitle) || this.name(),
  );

  /**
   * The eBay search link, or null when there is none to offer.
   *
   * <p>Passes on whatever German title {@link injectTitleMeta} already holds and never asks for one:
   * that signal is populated only while a preference needs it, and a request per row on page load
   * is the cost this replacement exists to avoid.
   */
  protected readonly ebayUrl = computed(() =>
    this.showEbayLink()
      ? ebaySearchUrl(
          this.userPrefsStore.ebayMarketplace(),
          this.year(),
          this.name(),
          this.meta()?.germanTitle,
        )
      : null,
  );
}
