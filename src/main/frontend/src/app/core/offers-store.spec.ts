import { provideHttpClient, withFetch } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { signal } from '@angular/core';
import { OffersStore } from './offers-store';
import { imdbId } from './domain';
import { TitleOffers } from './models';

const HEAT = imdbId('tt0113277');
const UP = imdbId('tt1049413');

const FETCHED: TitleOffers = {
  status: 'FETCHED',
  buyNow: { amountCents: 1299, shippingCents: 399, currency: 'EUR', url: 'https://www.ebay.de/itm/1' },
  auction: null,
  fetchedAt: '2026-09-05T14:00:00Z',
};

describe('OffersStore', () => {
  let store: OffersStore;
  let httpMock: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(withFetch()), provideHttpClientTesting()],
    });
    store = TestBed.inject(OffersStore);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpMock.verify());

  it('starts idle and asks for nothing on its own', () => {
    const state = store.stateFor(signal(HEAT));

    // The whole feature rests on this: a lookup only ever happens because someone clicked.
    expect(state().kind).toBe('idle');
    httpMock.expectNone(() => true);
  });

  it('load() goes through loading to loaded', () => {
    const state = store.stateFor(signal(HEAT));

    store.load(HEAT);
    expect(state().kind).toBe('loading');

    httpMock.expectOne((r) => r.url.endsWith('/api/titles/tt0113277/offers')).flush(FETCHED);
    expect(state()).toEqual({ kind: 'loaded', offers: FETCHED });
  });

  it('load() while a lookup is in flight does not start a second one', () => {
    store.load(HEAT);
    store.load(HEAT);

    // Two calls would cost four from the shared daily budget for one title.
    httpMock.expectOne((r) => r.url.endsWith('/api/titles/tt0113277/offers')).flush(FETCHED);
  });

  it('load() on an already loaded title does nothing', () => {
    store.load(HEAT);
    httpMock.expectOne(() => true).flush(FETCHED);

    store.load(HEAT);

    // A second click on a price that is already on screen must not quietly spend two more calls.
    httpMock.expectNone(() => true);
  });

  it('refresh() re-checks a loaded title with a changing cache-buster', () => {
    store.load(HEAT);
    httpMock.expectOne(() => true).flush(FETCHED);

    store.refresh(HEAT);
    const first = httpMock.expectOne((r) => r.urlWithParams.includes('refresh='));
    first.flush(FETCHED);
    store.refresh(HEAT);
    const second = httpMock.expectOne((r) => r.urlWithParams.includes('refresh='));

    expect(second.request.urlWithParams).not.toEqual(first.request.urlWithParams);
    second.flush(FETCHED);
  });

  it('a failed request ends in error, distinct from the server saying UNAVAILABLE', () => {
    const state = store.stateFor(signal(HEAT));

    store.load(HEAT);
    httpMock.expectOne(() => true).flush(null, { status: 500, statusText: 'Server Error' });

    expect(state().kind).toBe('error');
  });

  it('an error does not block a later retry', () => {
    store.load(HEAT);
    httpMock.expectOne(() => true).flush(null, { status: 500, statusText: 'Server Error' });

    store.load(HEAT);

    httpMock.expectOne((r) => r.url.endsWith('/api/titles/tt0113277/offers')).flush(FETCHED);
  });

  it('keeps titles apart', () => {
    const heat = store.stateFor(signal(HEAT));
    const up = store.stateFor(signal(UP));

    store.load(HEAT);
    httpMock.expectOne((r) => r.url.includes('tt0113277')).flush(FETCHED);

    expect(heat().kind).toBe('loaded');
    expect(up().kind).toBe('idle');
  });

  it('a server-side refusal is a loaded state, not an error', () => {
    const state = store.stateFor(signal(HEAT));
    const refused: TitleOffers = {
      status: 'GLOBAL_BUDGET_EXHAUSTED', buyNow: null, auction: null, fetchedAt: null,
    };

    store.load(HEAT);
    httpMock.expectOne(() => true).flush(refused);

    // The distinction matters: "budget spent" is something the user can be told about precisely,
    // a transport failure is not.
    expect(state()).toEqual({ kind: 'loaded', offers: refused });
  });
});
