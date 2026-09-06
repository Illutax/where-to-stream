/**
 * Builds the eBay search deep link shown next to a title (TODO-57).
 *
 * <p>This replaces the withdrawn price lookup (TODO-56), and the difference in cost is what shapes
 * the design. The lookup spent two calls from a shared daily budget per title, so its query had to
 * be as cheap as possible; a link costs nothing whether it is built or never clicked. The query is
 * therefore made as precise as we can make it — category-filtered and sorted by total price —
 * rather than as sparing as possible.
 *
 * <p>Everything here happens in the browser: no server call, no quota, no developer account. That
 * is the point — the predecessor died waiting for one.
 */

import { computed, inject, Signal } from '@angular/core';
import { ReleaseYear } from './domain';
import { EbayMarketplace } from './models';
import { UserPrefsStore } from './user-prefs-store';

/** What one marketplace needs for a search link. */
interface MarketplaceSearch {
  readonly host: string;
  /**
   * eBay category to restrict the search to, or null to search everything.
   *
   * <p>This matters more here than it would have for the old lookup, because of the sort below:
   * ordering by lowest price without a category puts the cheapest *matching junk* first — a poster,
   * a keychain, an empty case — where relevance ordering would have buried it.
   *
   * <p>{@code 617} is "DVDs & Blu-ray Discs". Verified against eBay (TODO-58, 2026-09-07): the
   * result page names the category in its title on {@code .de} ("DVDs & Blu-rays") and on
   * {@code .com} ("… in DVDs & Blu-ray Discs for sale"). {@code .co.uk} never names it, so the
   * evidence there is indirect but consistent — the filter narrows a film search from 296 to 193
   * hits and "kettle" from 32,000 to 370.
   *
   * <p>Still held per marketplace rather than as one constant: eBay does not guarantee category ids
   * across sites, so the day one of them diverges, only this table changes — and setting an entry
   * to null drops the filter for that marketplace alone.
   */
  readonly categoryId: string | null;
  /**
   * Whether the German title is the better search term here.
   *
   * <p>"Der Pate 1972" finds more on ebay.de than "The Godfather 1972" does; on the English-language
   * sites the reverse holds.
   */
  readonly prefersGermanTitle: boolean;
}

const MARKETPLACES: Record<EbayMarketplace, MarketplaceSearch> = {
  EBAY_DE: { host: 'www.ebay.de', categoryId: '617', prefersGermanTitle: true },
  EBAY_US: { host: 'www.ebay.com', categoryId: '617', prefersGermanTitle: false },
  EBAY_GB: { host: 'www.ebay.co.uk', categoryId: '617', prefersGermanTitle: false },
};

/** Where an unknown marketplace lands — see {@link ebaySearchUrl}. */
const FALLBACK_MARKETPLACE: EbayMarketplace = 'EBAY_DE';

/**
 * eBay's "price + shipping: lowest first" ordering.
 *
 * <p>This is where the link answers the question the price lookup was built for. That feature
 * turned "what does this cost at least?" into a number; here the first row of the result list is
 * the same answer. It includes shipping for the same reason the old comparison did — a cheap disc
 * with expensive postage is not the cheaper offer.
 *
 * <p>Verified against eBay (TODO-58, 2026-09-07): on {@code .de} the first results come back at
 * 1.50 / 2.49 / 3.00 / 3.90 EUR plus postage, ascending; on {@code .com} the ordering visibly
 * shifts towards the cheaper listings compared with the same search without the parameter. Note
 * that {@code 12}, the neighbouring value, is *not* the ascending one — it was the obvious guess
 * and it is wrong.
 */
const SORT_BY_LOWEST_TOTAL = '15';

/**
 * The search URL for one title, or null when there should be no link at all.
 *
 * <p>Null for an unreleased title ({@link ReleaseYear} 0 or absent). Nothing that has not been
 * released is being sold second-hand, and a search for the bare title — sorted by price, in a
 * category full of unrelated discs — returns noise dressed up as an answer. Dropping the link is
 * the honest output; it also keeps the search term free of a special case, since it now always
 * carries both title and year or does not exist.
 *
 * <p>An unknown marketplace falls back to {@link FALLBACK_MARKETPLACE} rather than throwing. The
 * server takes the same line for the same value (see {@code Marketplace.byId}): a stale preference
 * should not be able to take a page down, and here it would — this runs inside a `computed` during
 * render, once per row.
 *
 * @param germanTitle the German title if the caller already has one, else null. Never fetch one
 *   just for this link: a request per title on page load is exactly what this replacement exists
 *   to avoid.
 */
export function ebaySearchUrl(
  marketplace: EbayMarketplace,
  year: ReleaseYear | null,
  name: string,
  germanTitle: string | null,
): string | null {
  if (year === null || year <= 0) {
    return null;
  }
  const site = MARKETPLACES[marketplace] ?? MARKETPLACES[FALLBACK_MARKETPLACE];
  const title = ((site.prefersGermanTitle && germanTitle) || name).trim();
  if (!title) {
    return null;
  }
  const params = new URLSearchParams({ _nkw: `${title} ${year}`, _sop: SORT_BY_LOWEST_TOTAL });
  if (site.categoryId) {
    params.set('_sacat', site.categoryId);
  }
  return `https://${site.host}/sch/i.html?${params}`;
}

/** What a component has to offer before a search link can be built for its row. */
export interface EbaySearchSource {
  /** Whether this view wants the link at all — off outside the dashboard. */
  readonly enabled: () => boolean;
  /** The title as the server delivered it (the original, usually English). */
  readonly name: () => string;
  /** The release year, or null where the row never carried a machine-readable one. */
  readonly year: () => ReleaseYear | null;
  /** Whatever German title the row already holds, without fetching one for this purpose. */
  readonly germanTitle: () => string | null;
}

/**
 * The search link for one row as a signal, or null where there is none.
 *
 * <p>Exists so the rule lives once rather than in every component that renders a title. Both the
 * table cell and the poster tile show the same link, and a divergence between them would be a
 * dashboard that searches for two different things depending on the view mode.
 *
 * <p>Must be called from an injection context, mirroring {@link injectTitleMeta}.
 *
 * <p>The German title is used only while the user has that preference on. Not for tidiness: the
 * metadata that carries it is fetched whenever *either* the age-rating or the German-title
 * preference is on, so taking it whenever it happens to be there made the search term depend on
 * the age-rating toggle — an unrelated setting — and change under the cursor once the fetch
 * resolved. Tying it to the preference that is actually about titles makes the link match what the
 * row displays.
 */
export function injectEbaySearchUrl(source: EbaySearchSource): Signal<string | null> {
  const userPrefs = inject(UserPrefsStore);
  return computed(() =>
    source.enabled()
      ? ebaySearchUrl(
          userPrefs.ebayMarketplace(),
          source.year(),
          source.name(),
          userPrefs.showGermanTitle() ? source.germanTitle() : null,
        )
      : null,
  );
}
