import { releaseYear } from './domain';
import { ebaySearchUrl } from './ebay-search';

describe('ebaySearchUrl', () => {
  const year = releaseYear(1995);

  it('searches the marketplace the user picked', () => {
    const hosts = (['EBAY_DE', 'EBAY_US', 'EBAY_GB'] as const).map(
      (m) => new URL(ebaySearchUrl(m, year, 'Heat')!).host,
    );

    expect(hosts).toEqual(['www.ebay.de', 'www.ebay.com', 'www.ebay.co.uk']);
  });

  it('asks for title and year, in the movie category, cheapest total first', () => {
    const url = new URL(ebaySearchUrl('EBAY_DE', year, 'Heat')!);

    // Read off the parsed URL rather than the raw string: what matters is the query eBay receives,
    // not how the space in "Heat 1995" happens to be spelled on the way there.
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
    const url = new URL(ebaySearchUrl('EBAY_US', releaseYear(2005), 'Mr. & Mrs. Smith?')!);

    expect(url.searchParams.get('_nkw')).toBe('Mr. & Mrs. Smith? 2005');
  });

  it('prefers the German title on ebay.de and the original everywhere else', () => {
    const terms = (['EBAY_DE', 'EBAY_US', 'EBAY_GB'] as const).map(
      (m) => new URL(ebaySearchUrl(m, releaseYear(1972), 'The Godfather', 'Der Pate')!).searchParams.get('_nkw'),
    );

    expect(terms).toEqual(['Der Pate 1972', 'The Godfather 1972', 'The Godfather 1972']);
  });

  it('falls back to the original name when no German title was loaded', () => {
    // The German title is only there while a preference happens to have fetched it — the link must
    // never be the reason a request goes out, so "absent" is the normal case, not an error.
    const withNull = new URL(ebaySearchUrl('EBAY_DE', releaseYear(1972), 'The Godfather', null)!);
    const omitted = new URL(ebaySearchUrl('EBAY_DE', releaseYear(1972), 'The Godfather')!);

    expect([withNull.searchParams.get('_nkw'), omitted.searchParams.get('_nkw')]).toEqual([
      'The Godfather 1972',
      'The Godfather 1972',
    ]);
  });

  it('offers no link for an unreleased title', () => {
    expect(ebaySearchUrl('EBAY_DE', releaseYear(0), 'Avatar 5')).toBeNull();
  });
});
