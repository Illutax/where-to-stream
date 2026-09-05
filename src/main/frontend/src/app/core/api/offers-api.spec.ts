import { provideHttpClient, withFetch } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { OffersApi } from './offers-api';
import { imdbId } from '../domain';

describe('OffersApi', () => {
  let api: OffersApi;
  let httpMock: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(withFetch()), provideHttpClientTesting()],
    });
    api = TestBed.inject(OffersApi);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpMock.verify());

  it('GETs "../api/titles/{id}/offers" with no query parameter by default', () => {
    let result: unknown = null;
    api.get(imdbId('tt0113277')).subscribe((offers) => (result = offers));

    const req = httpMock.expectOne((r) => r.url.endsWith('/api/titles/tt0113277/offers'));
    // No parameter on the first load: the browser cache is welcome to answer a re-render.
    expect(req.request.urlWithParams).not.toContain('refresh');
    req.flush({ status: 'FETCHED', buyNow: null, auction: null, fetchedAt: null });

    expect(result).toEqual({ status: 'FETCHED', buyNow: null, auction: null, fetchedAt: null });
  });

  it('appends a cache-buster when one is given', () => {
    api.get(imdbId('tt0113277'), 7).subscribe();

    // Without this a deliberate re-check would be answered from the browser's own 5-minute cache and
    // the button would look broken.
    const req = httpMock.expectOne((r) => r.urlWithParams.includes('refresh=7'));
    req.flush({ status: 'FETCHED', buyNow: null, auction: null, fetchedAt: null });
  });

  it('offers no way to pass a search term', () => {
    // The signature is the guard: the server builds the eBay query from its own data, which is what
    // keeps this endpoint from being an authenticated proxy to eBay search.
    expect(api.get.length).toBe(2);
  });
});
