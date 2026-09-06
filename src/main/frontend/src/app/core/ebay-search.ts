import { ReleaseYear } from './domain';
import { EbayMarketplace } from './models';

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
   * <p><strong>Unverified.</strong> {@code 617} ("DVDs & Blu-ray Discs") comes from the research in
   * {@code docs/EBAY_PRICE_LOOKUP_PLAN.md} and could not be checked from the build container —
   * eBay answers automated requests with 403. It is held per marketplace rather than as one
   * constant precisely because eBay does not guarantee category ids across sites: if `.com` or
   * `.co.uk` turns out to use different ids, only this table changes, and setting an entry to null
   * drops the filter for that marketplace alone.
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

/**
 * eBay's "price + shipping: lowest first" ordering.
 *
 * <p>This is where the link answers the question the price lookup was built for. That feature
 * turned "what does this cost at least?" into a number; here the first row of the result list is
 * the same answer. It includes shipping for the same reason the old comparison did — a cheap disc
 * with expensive postage is not the cheaper offer.
 *
 * <p><strong>The value is unverified</strong>, for the same reason as the category above. If it is
 * wrong, drop the parameter rather than guessing another number: eBay's default ordering is a
 * worse answer, but it is not a wrong one.
 */
const SORT_BY_LOWEST_TOTAL = '15';

/**
 * The search URL for one title, or null when there should be no link at all.
 *
 * <p>Null for an unreleased title ({@link ReleaseYear} 0). Nothing that has not been released is
 * being sold second-hand, and a search for the bare title — sorted by price, in a category full of
 * unrelated discs — returns noise dressed up as an answer. Dropping the link is the honest output;
 * it also keeps the search term free of a special case, since it now always carries both title and
 * year or does not exist.
 *
 * @param germanTitle the German title if it happens to be loaded already, else null. Never fetch
 *   one just for this link: a request per title on page load is exactly what this replacement
 *   exists to avoid. Callers pass what {@code injectTitleMeta} already holds, which is populated
 *   only while the age-rating or German-title preference is on.
 */
export function ebaySearchUrl(
  marketplace: EbayMarketplace,
  year: ReleaseYear,
  name: string,
  germanTitle?: string | null,
): string | null {
  if (year <= 0) {
    return null;
  }
  const site = MARKETPLACES[marketplace];
  const title = (site.prefersGermanTitle && germanTitle) || name;
  const params = new URLSearchParams({ _nkw: `${title} ${year}` });
  if (site.categoryId) {
    params.set('_sacat', site.categoryId);
  }
  params.set('_sop', SORT_BY_LOWEST_TOTAL);
  return `https://${site.host}/sch/i.html?${params}`;
}
