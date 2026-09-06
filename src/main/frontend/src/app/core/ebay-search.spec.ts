import { provideHttpClient, withFetch } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { releaseYear } from './domain';
import { ebaySearchUrl, injectEbaySearchUrl } from './ebay-search';
import { EbayMarketplace } from './models';
import { UserPrefsStore } from './user-prefs-store';

describe('ebaySearchUrl', () => {
  const year = releaseYear(1995);
  const MARKETPLACES = ['EBAY_DE', 'EBAY_US', 'EBAY_GB'] as const;

  it('searches the marketplace the user picked', () => {
    const hosts = MARKETPLACES.map((m) => new URL(ebaySearchUrl(m, year, 'Heat', null)!).host);

    expect(hosts).toEqual(['www.ebay.de', 'www.ebay.com', 'www.ebay.co.uk']);
  });

  it('asks for title and year, in the movie category, cheapest total first', () => {
    // Both eBay parameters were checked against the live site (TODO-58): 617 is the disc category,
    // 15 the ascending price-plus-postage sort. Pinned here because they are magic numbers in
    // someone else's system — the check does not stop eBay from renumbering.
    const url = new URL(ebaySearchUrl('EBAY_DE', year, 'Heat', null)!);

    expect([url.pathname, ...['_nkw', '_sacat', '_sop'].map((p) => url.searchParams.get(p))]).toEqual([
      '/sch/i.html',
      'Heat 1995',
      '617',
      '15',
    ]);
  });

  it('encodes a title that would otherwise change the query', () => {
    // "&" and "?" in a title are the case a hand-built query string gets wrong, and the failure is
    // silent: the search still opens, just for something else.
    const url = new URL(ebaySearchUrl('EBAY_US', releaseYear(2005), 'Mr. & Mrs. Smith?', null)!);

    expect(url.searchParams.get('_nkw')).toBe('Mr. & Mrs. Smith? 2005');
  });

  it('prefers the German title on ebay.de and the original everywhere else', () => {
    const terms = MARKETPLACES.map((m) =>
      new URL(ebaySearchUrl(m, releaseYear(1972), 'The Godfather', 'Der Pate')!).searchParams.get('_nkw'),
    );

    expect(terms).toEqual(['Der Pate 1972', 'The Godfather 1972', 'The Godfather 1972']);
  });

  it('falls back to the original name when no German title was passed', () => {
    // The German title is only ever handed in when it is already loaded — the link must never be
    // the reason a request goes out, so "absent" is the normal case, not an error.
    const url = new URL(ebaySearchUrl('EBAY_DE', releaseYear(1972), 'The Godfather', null)!);

    expect(url.searchParams.get('_nkw')).toBe('The Godfather 1972');
  });

  it('falls back to the default marketplace instead of throwing on an unknown one', () => {
    // A stale preference must not be able to take the dashboard down: this runs inside a computed
    // during render, once per row. The server takes the same line for the same value
    // (Marketplace.byId).
    const url = new URL(ebaySearchUrl('EBAY_MOON' as EbayMarketplace, year, 'Heat', null)!);

    expect(url.host).toBe('www.ebay.de');
  });

  it('offers no link without a year to search for, and none for a title that is not out yet', () => {
    // Nothing unreleased is being sold second-hand; a price-sorted search would return noise.
    // A negative year cannot occur today — the guard is `<= 0` rather than `=== 0` anyway, and
    // that is only worth writing if it is also held.
    const missing = ebaySearchUrl('EBAY_DE', null, 'Avatar 5', null);
    const unreleased = ebaySearchUrl('EBAY_DE', releaseYear(0), 'Avatar 5', null);
    const negative = ebaySearchUrl('EBAY_DE', releaseYear(-1), 'Avatar 5', null);

    expect([missing, unreleased, negative]).toEqual([null, null, null]);
  });

  it('offers no link when there is no title to search for', () => {
    // Otherwise `_nkw` becomes " 1995" and the link opens the cheapest arbitrary disc in the
    // category. Whitespace counts as absent for the same reason.
    const empty = ebaySearchUrl('EBAY_DE', year, '', null);
    const blank = ebaySearchUrl('EBAY_DE', year, '   ', null);

    expect([empty, blank]).toEqual([null, null]);
  });
});

describe('injectEbaySearchUrl', () => {
  let prefs: UserPrefsStore;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(withFetch()), provideHttpClientTesting()],
    });
    prefs = TestBed.inject(UserPrefsStore);
  });

  const link = (over: { enabled?: boolean; germanTitle?: string | null } = {}) =>
    TestBed.runInInjectionContext(() =>
      injectEbaySearchUrl({
        enabled: signal(over.enabled ?? true),
        name: signal('The Godfather'),
        year: signal(releaseYear(1972)),
        germanTitle: signal(over.germanTitle ?? null),
      }),
    );

  it('uses the German title only while the user asked for German titles', () => {
    // The metadata carrying the German title is fetched whenever the age-rating *or* the
    // German-title preference is on. Taking it whenever it happened to be there made the search
    // term depend on the age-rating toggle and change under the cursor once the fetch resolved.
    prefs.init({ ebayMarketplace: 'EBAY_DE', showGermanTitle: true, showAgeRatings: false });
    const wanted = link({ germanTitle: 'Der Pate' })();

    prefs.init({ ebayMarketplace: 'EBAY_DE', showGermanTitle: false, showAgeRatings: true });
    const unwanted = link({ germanTitle: 'Der Pate' })();

    expect([wanted, unwanted].map((u) => new URL(u!).searchParams.get('_nkw'))).toEqual([
      'Der Pate 1972',
      'The Godfather 1972',
    ]);
  });

  it('follows the marketplace preference without the caller passing it', () => {
    prefs.init({ ebayMarketplace: 'EBAY_US' });

    expect(new URL(link()()!).host).toBe('www.ebay.com');
  });

  it('offers nothing at all where the view did not ask for a link', () => {
    expect(link({ enabled: false })()).toBeNull();
  });
});
