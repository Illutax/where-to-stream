import { provideHttpClient, withFetch } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { cheaperOffer, formatPrice, OfferPrices, offerTotalCents } from './offer-prices';
import { imdbId } from '../../core/domain';
import { Offer, TitleOffers } from '../../core/models';
import { translocoTesting } from '../../testing/transloco-testing';

const HEAT = imdbId('tt0113277');

const BUY_NOW: Offer = {
  amountCents: 1299, shippingCents: 399, currency: 'EUR', url: 'https://www.ebay.de/itm/1',
};
const AUCTION: Offer = {
  amountCents: 450, shippingCents: null, currency: 'EUR', url: 'https://www.ebay.de/itm/2',
};

function response(overrides: Partial<TitleOffers> = {}): TitleOffers {
  return {
    status: 'FETCHED',
    buyNow: BUY_NOW,
    auction: AUCTION,
    fetchedAt: '2026-09-05T14:00:00Z',
    ...overrides,
  };
}

describe('offer helpers', () => {
  it('counts stated shipping towards the total', () => {
    expect(offerTotalCents(BUY_NOW)).toBe(1698);
  });

  it('leaves unstated shipping out rather than treating it as zero cost of a known kind', () => {
    expect(offerTotalCents(AUCTION)).toBe(450);
  });

  it('picks the cheaper offer by total, not by item price', () => {
    const cheapItemExpensivePostage: Offer = { ...BUY_NOW, amountCents: 400, shippingCents: 900 };
    const dearerItemFreePostage: Offer = { ...AUCTION, amountCents: 800, shippingCents: 0 };

    expect(cheaperOffer(cheapItemExpensivePostage, dearerItemFreePostage))
      .toBe(dearerItemFreePostage);
  });

  it('falls back to whichever offer exists', () => {
    expect(cheaperOffer(null, AUCTION)).toBe(AUCTION);
    expect(cheaperOffer(BUY_NOW, null)).toBe(BUY_NOW);
    expect(cheaperOffer(null, null)).toBeNull();
  });

  it('formats minor units as a localised amount', () => {
    expect(formatPrice(1299, 'EUR', 'de-DE')).toContain('12,99');
    expect(formatPrice(1299, 'GBP', 'en-GB')).toContain('12.99');
  });
});

describe('OfferPrices', () => {
  let fixture: ComponentFixture<OfferPrices>;
  let httpMock: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [OfferPrices, translocoTesting()],
      providers: [provideHttpClient(withFetch()), provideHttpClientTesting(), provideNoopAnimations()],
    });
    fixture = TestBed.createComponent(OfferPrices);
    fixture.componentRef.setInput('imdbId', HEAT);
    fixture.componentRef.setInput('name', 'Heat');
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpMock.verify());

  function text(): string {
    return fixture.nativeElement.textContent as string;
  }

  function loadAndFlush(offers: TitleOffers): void {
    fixture.detectChanges();
    (fixture.nativeElement.querySelector('.offer-load') as HTMLButtonElement).click();
    fixture.detectChanges();
    httpMock.expectOne((r) => r.url.endsWith('/api/titles/tt0113277/offers')).flush(offers);
    fixture.detectChanges();
  }

  it('shows a button and fetches nothing until it is pressed', () => {
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('.offer-load')).not.toBeNull();
    // The rule the whole feature rests on: no lookup without a deliberate click.
    httpMock.expectNone(() => true);
  });

  it('shows a skeleton bar while loading, not a spinner', () => {
    fixture.detectChanges();
    (fixture.nativeElement.querySelector('.offer-load') as HTMLButtonElement).click();
    fixture.detectChanges();

    // Project convention (CLAUDE.md): placeholder shaped like the content, never a spinner.
    expect(fixture.nativeElement.querySelector('.skeleton-bar')).not.toBeNull();
    httpMock.expectOne(() => true).flush(response());
  });

  it('shows the cheaper of the two prices inline, prefixed with "from"', () => {
    loadAndFlush(response());

    // The auction at 4.50 beats the fixed price at 12.99 + 3.99. Formatted for the store's default
    // language (EN → en-GB), which is why the decimal separator is a dot here.
    expect(text()).toContain('4.50');
    // "from" is what tells a touch user, who has no hover, that a second price exists at all.
    expect(text()).toContain('from');
  });

  it('carries both prices and the timestamp in the tooltip', () => {
    loadAndFlush(response());

    // Asserted on the component's computed value rather than the DOM: the tooltip overlay only
    // exists while it is open, so there is nothing to query otherwise.
    const details = fixture.componentInstance['details']();
    expect(details).toContain('Buy it now');
    expect(details).toContain('Current bid');
    expect(details).toContain('As of');
    // A plain string, never markup — the amounts and URL originate in an eBay response.
    expect(details).not.toContain('<');
  });

  it('says shipping is unknown rather than implying it is free', () => {
    loadAndFlush(response());

    // AUCTION has shippingCents: null. Rendering that as 0 would be a lie about the total.
    expect(fixture.componentInstance['details']()).toContain('shipping cost unknown');
  });

  it('links the price to the offer with rel="noopener" and no markup anywhere', () => {
    loadAndFlush(response());

    const link = fixture.nativeElement.querySelector('.offer-price') as HTMLAnchorElement;
    expect(link.getAttribute('href')).toBe('https://www.ebay.de/itm/2');
    expect(link.getAttribute('target')).toBe('_blank');
    expect(link.getAttribute('rel')).toContain('noopener');
    expect(link.innerHTML).not.toContain('<');
  });

  it('offers an explicit way to re-check, which bypasses the browser cache', () => {
    loadAndFlush(response());

    (fixture.nativeElement.querySelector('.offer-refresh') as HTMLButtonElement).click();
    fixture.detectChanges();

    httpMock.expectOne((r) => r.urlWithParams.includes('refresh=')).flush(response());
  });

  it('distinguishes "no offers" from the two budget refusals and from unavailable', () => {
    const cases: [TitleOffers['status'], string][] = [
      ['FETCHED', 'No offers'],
      ['UNAVAILABLE', 'Currently unavailable'],
      ['USER_ALLOWANCE_REACHED', 'Your daily allowance is used up'],
      ['GLOBAL_BUDGET_EXHAUSTED', 'Used up for today'],
    ];

    for (const [status, expected] of cases) {
      const local = TestBed.createComponent(OfferPrices);
      local.componentRef.setInput('imdbId', imdbId(`tt000${status.length}`));
      local.detectChanges();
      (local.nativeElement.querySelector('.offer-load') as HTMLButtonElement).click();
      local.detectChanges();
      httpMock
        .expectOne((r) => r.url.includes(`tt000${status.length}`))
        .flush({ status, buyNow: null, auction: null, fetchedAt: null });
      local.detectChanges();

      // Four different things to say, because they call for four different reactions.
      expect(local.nativeElement.textContent).toContain(expected);
    }
  });

  it('reports a transport failure as unavailable rather than crashing', () => {
    fixture.detectChanges();
    (fixture.nativeElement.querySelector('.offer-load') as HTMLButtonElement).click();
    fixture.detectChanges();
    httpMock.expectOne(() => true).flush(null, { status: 500, statusText: 'Server Error' });
    fixture.detectChanges();

    expect(text()).toContain('Currently unavailable');
  });
});
