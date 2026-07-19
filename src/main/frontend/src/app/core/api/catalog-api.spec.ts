import { provideHttpClient, withFetch } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { CatalogApi } from './catalog-api';
import { OverviewEntry } from '../models';

describe('CatalogApi', () => {
  let api: CatalogApi;
  let httpMock: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(withFetch()), provideHttpClientTesting()],
    });
    api = TestBed.inject(CatalogApi);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpMock.verify());

  it('requests the catalogue from the "../api/"-derived base and returns the body', () => {
    const payload: OverviewEntry[] = [
      { isRated: true, name: 'Movie', imdbId: 'tt1', year: 2020, added: '2020-01-01', services: 'Netflix' },
    ];

    let received: OverviewEntry[] | undefined;
    api.getCatalog().subscribe((entries) => (received = entries));

    const req = httpMock.expectOne((r) => r.url.endsWith('/api/catalog'));
    expect(req.request.method).toBe('GET');
    req.flush(payload);

    expect(received).toEqual(payload);
  });
});
