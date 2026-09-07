import { provideHttpClient, withFetch } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { imdbId, releaseYear } from '../../core/domain';
import { UserPrefsStore } from '../../core/user-prefs-store';
import { translocoTesting } from '../../testing/transloco-testing';
import { EbayLink } from './ebay-link';

describe('EbayLink', () => {
  let fixture: ComponentFixture<EbayLink>;
  let httpMock: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [EbayLink, translocoTesting()],
      providers: [provideHttpClient(withFetch()), provideHttpClientTesting()],
    });
    fixture = TestBed.createComponent(EbayLink);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpMock.verify());

  const anchor = () => fixture.nativeElement.querySelector('a') as HTMLAnchorElement | null;
  const wordmark = () => fixture.nativeElement.querySelector('.wordmark') as HTMLElement | null;

  /** Renders with both metadata preferences off unless `prefs` says otherwise, so nothing is fetched. */
  const render = ({ prefs, ...inputs }: { prefs?: object } & Record<string, unknown>) => {
    TestBed.inject(UserPrefsStore).init({ showAgeRatings: false, showGermanTitle: false, ...prefs });
    Object.entries(inputs).forEach(([k, v]) => fixture.componentRef.setInput(k, v));
    fixture.detectChanges();
  };

  it('opens the marketplace search in a new tab, cheapest total first, and names the title', () => {
    render({ imdbId: imdbId('tt1'), name: 'Heat', releaseYear: releaseYear(1995) });

    // `rel` without `target` would guard nothing, and a row of links all reading "Search" is
    // unusable with a screen reader unless the accessible name carries the title.
    const url = new URL(anchor()!.href);
    expect([
      url.host,
      url.pathname,
      url.searchParams.get('_nkw'),
      url.searchParams.get('_sacat'),
      url.searchParams.get('_sop'),
      anchor()!.target,
      anchor()!.rel,
      anchor()!.getAttribute('aria-label'),
    ]).toEqual([
      'www.ebay.de',
      '/sch/i.html',
      'Heat 1995',
      '617',
      '15',
      '_blank',
      'noopener',
      'Search eBay for Heat, cheapest first',
    ]);
  });

  it('reads as a word in a table cell and as the eBay mark under a poster', () => {
    render({ imdbId: imdbId('tt1'), name: 'Heat', releaseYear: releaseYear(1995) });
    const asText = [anchor()!.textContent?.trim(), wordmark()];

    fixture.componentRef.setInput('appearance', 'badge');
    fixture.detectChanges();

    expect([asText, [anchor()!.textContent?.trim(), wordmark()!.textContent]]).toEqual([
      ['Search', null],
      ['ebay', 'ebay'],
    ]);
  });

  it('paints the badge in the four brand colours and hides it from the accessible name', () => {
    // The wordmark is a logo, not copy: it must not be read out on top of the aria-label, and the
    // colours are the recognisable part, so they are asserted rather than left to drift.
    render({ imdbId: imdbId('tt1'), name: 'Heat', releaseYear: releaseYear(1995), appearance: 'badge' });

    const letters = Array.from(wordmark()!.querySelectorAll('span')).map((s) => getComputedStyle(s).color);
    expect([wordmark()!.getAttribute('aria-hidden'), ...letters]).toEqual([
      'true',
      'rgb(229, 50, 56)', // e — red
      'rgb(0, 100, 210)', // b — blue
      'rgb(245, 175, 2)', // a — yellow
      'rgb(134, 184, 23)', // y — light green
    ]);
  });

  it('searches for the German title on ebay.de once the row is showing it', () => {
    render({
      imdbId: imdbId('tt1'),
      name: 'The Godfather',
      releaseYear: releaseYear(1972),
      prefs: { showGermanTitle: true },
    });
    httpMock.expectOne((r) => r.url.endsWith('/api/titles/tt1/meta'))
      .flush({ rating: null, germanTitle: 'Der Pate' });
    fixture.detectChanges();

    expect([new URL(anchor()!.href).searchParams.get('_nkw'), anchor()!.getAttribute('aria-label')])
      .toEqual(['Der Pate 1972', 'Search eBay for Der Pate, cheapest first']);
  });

  it('renders nothing for a title that is not out yet, and nothing without a year', () => {
    render({ imdbId: imdbId('tt1'), name: 'Avatar 5', releaseYear: releaseYear(0) });
    const unreleased = anchor();

    fixture.componentRef.setInput('releaseYear', null);
    fixture.detectChanges();

    expect([unreleased, anchor()]).toEqual([null, null]);
  });
});
