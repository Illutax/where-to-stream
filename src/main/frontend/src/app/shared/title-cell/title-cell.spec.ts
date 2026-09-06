import { provideHttpClient, withFetch } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { TitleCell } from './title-cell';
import { imdbId, releaseYear } from '../../core/domain';
import { UserPrefsStore } from '../../core/user-prefs-store';
import { translocoTesting } from '../../testing/transloco-testing';

describe('TitleCell', () => {
  let fixture: ComponentFixture<TitleCell>;
  let httpMock: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [TitleCell, translocoTesting()],
      providers: [provideHttpClient(withFetch()), provideHttpClientTesting()],
    });
    fixture = TestBed.createComponent(TitleCell);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpMock.verify());

  const link = () => fixture.nativeElement.querySelector('a') as HTMLAnchorElement;
  const badge = () => fixture.nativeElement.querySelector('.age-badge') as HTMLElement | null;
  const ebayLink = () => fixture.nativeElement.querySelector('.ebay-link') as HTMLAnchorElement | null;

  /** Renders the cell with both preferences off, so nothing is fetched. */
  const renderPlain = (inputs: Record<string, unknown>) => {
    TestBed.inject(UserPrefsStore).init({ showAgeRatings: false, showGermanTitle: false });
    Object.entries(inputs).forEach(([k, v]) => fixture.componentRef.setInput(k, v));
    fixture.detectChanges();
  };

  it('shows the English name and the FSK badge when age ratings are on', () => {
    // UserPrefsStore.showAgeRatings defaults on → the cell fetches its metadata.
    fixture.componentRef.setInput('imdbId', imdbId('tt1'));
    fixture.componentRef.setInput('name', 'The Godfather');
    fixture.detectChanges();

    httpMock.expectOne((r) => r.url.endsWith('/api/titles/tt1/meta'))
      .flush({ rating: { system: 'FSK', label: '16' }, germanTitle: 'Der Pate' });
    fixture.detectChanges();

    expect(link().textContent?.trim()).toBe('The Godfather'); // German-title toggle off → English
    expect(badge()?.textContent?.trim()).toBe('16');
  });

  it('shows the German title when that preference is on and one exists', () => {
    TestBed.inject(UserPrefsStore).init({ showGermanTitle: true, showAgeRatings: false }); // only the German-title preference is on
    fixture.componentRef.setInput('imdbId', imdbId('tt1'));
    fixture.componentRef.setInput('name', 'Up');
    fixture.detectChanges();

    httpMock.expectOne((r) => r.url.endsWith('/api/titles/tt1/meta'))
      .flush({ rating: null, germanTitle: 'Oben' });
    fixture.detectChanges();

    expect(link().textContent?.trim()).toBe('Oben');
    expect(badge()).toBeNull(); // age ratings off
  });

  it('does not fetch or change the title when both preferences are off', () => {
    TestBed.inject(UserPrefsStore).init({ showAgeRatings: false, showGermanTitle: false });
    fixture.componentRef.setInput('imdbId', imdbId('tt1'));
    fixture.componentRef.setInput('name', 'Up');
    fixture.detectChanges();

    httpMock.expectNone((r) => r.url.includes('/meta'));
    expect(link().textContent?.trim()).toBe('Up');
  });

  it('offers a labelled eBay search, in a new tab, for the released title', () => {
    renderPlain({ imdbId: imdbId('tt1'), name: 'Heat', releaseYear: releaseYear(1995), showEbayLink: true });

    // The accessible name has to carry the title: two hundred rows whose only link text is "eBay"
    // are unusable with a screen reader. `rel` without `target` would guard nothing, so both.
    const url = new URL(ebayLink()!.href);
    expect([
      url.host,
      url.searchParams.get('_nkw'),
      url.searchParams.get('_sop'),
      ebayLink()!.target,
      ebayLink()!.rel,
      ebayLink()!.getAttribute('aria-label'),
    ]).toEqual(['www.ebay.de', 'Heat 1995', '15', '_blank', 'noopener', 'Search eBay for Heat, cheapest first']);
  });

  it('searches ebay.de for the German title the row is showing', () => {
    // Label, rendered title and search term have to name the same film. The link takes the German
    // title only while that preference is on — which is also what puts it on screen.
    TestBed.inject(UserPrefsStore).init({ showAgeRatings: false, showGermanTitle: true });
    fixture.componentRef.setInput('imdbId', imdbId('tt1'));
    fixture.componentRef.setInput('name', 'The Godfather');
    fixture.componentRef.setInput('releaseYear', releaseYear(1972));
    fixture.componentRef.setInput('showEbayLink', true);
    fixture.detectChanges();

    httpMock.expectOne((r) => r.url.endsWith('/api/titles/tt1/meta'))
      .flush({ rating: null, germanTitle: 'Der Pate' });
    fixture.detectChanges();

    expect([
      new URL(ebayLink()!.href).searchParams.get('_nkw'),
      ebayLink()!.getAttribute('aria-label'),
      link().textContent?.trim(),
    ]).toEqual(['Der Pate 1972', 'Search eBay for Der Pate, cheapest first', 'Der Pate']);
  });

  it('offers no eBay search where the cell is reused outside the dashboard', () => {
    renderPlain({ imdbId: imdbId('tt1'), name: 'Heat', releaseYear: releaseYear(1995) });

    expect(ebayLink()).toBeNull();
  });

  it('offers no eBay search for a title that is not out yet', () => {
    // Nothing unreleased is being sold second-hand; a price-sorted search would return noise.
    renderPlain({ imdbId: imdbId('tt1'), name: 'Avatar 5', releaseYear: releaseYear(0), showEbayLink: true });

    expect(ebayLink()).toBeNull();
  });
});
