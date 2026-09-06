import { ChangeDetectionStrategy, Component, computed, inject, input } from '@angular/core';
import { TranslocoPipe } from '@jsverse/transloco';
import { ImdbId, imdbUrl, ReleaseYear } from '../../core/domain';
import { injectEbaySearchUrl } from '../../core/ebay-search';
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
           [attr.aria-label]="'ebay.searchFor' | transloco: { name: displayTitle() }">{{ 'ebay.link' | transloco }}</a>
      }
    </div>
  `,
  styles: `
    /*
     * A link, so it takes the link colour rather than a fourth shade of its own.
     * \`flex: 0 0 auto\` mirrors .age-badge in the same flex row: without it the nowrap text
     * squeezes the title instead of keeping its own width.
     * The min-height and padding buy the 24px target WCAG 2.5.8 asks for -- inline text of
     * 0.78rem is about 15px tall on its own.
     */
    .ebay-link {
      flex: 0 0 auto;
      display: inline-flex;
      align-items: center;
      min-height: 24px;
      padding: 0 0.35rem;
      font-size: 0.78rem;
      white-space: nowrap;
      color: var(--mat-sys-primary);
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
  /**
   * Only for the eBay link — null where the caller has no machine-readable year; an unreleased
   * title (0) gets no link either. Null rather than a 0 default on purpose: a 0 default would make
   * a forgotten binding indistinguishable from "not released yet", and both would fail silently.
   */
  readonly releaseYear = input<ReleaseYear | null>(null);

  protected readonly userPrefsStore = inject(UserPrefsStore);
  protected readonly imdbUrl = imdbUrl;
  protected readonly meta = injectTitleMeta(() => this.imdbId());

  /** The German title when the preference is on and one exists, else the original (English) name. */
  protected readonly displayTitle = computed(
    () => (this.userPrefsStore.showGermanTitle() && this.meta()?.germanTitle) || this.name(),
  );

  /** The eBay search link, or null when there is none to offer — see {@link injectEbaySearchUrl}. */
  protected readonly ebayUrl = injectEbaySearchUrl({
    enabled: this.showEbayLink,
    name: this.name,
    year: this.releaseYear,
    germanTitle: () => this.meta()?.germanTitle ?? null,
  });
}
