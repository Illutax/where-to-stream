import { provideHttpClient, withFetch } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { imdbId, releaseYear } from '../domain';
import { ImdbSearchApi } from './imdb-search-api';

describe('ImdbSearchApi', () => {
  let api: ImdbSearchApi;
  let httpMock: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(withFetch()), provideHttpClientTesting()],
    });
    api = TestBed.inject(ImdbSearchApi);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpMock.verify());

  it('GETs "../api/imdb/search" with the query string', () => {
    let received: unknown;
    api.search('matrix').subscribe((r) => (received = r));

    const req = httpMock.expectOne((r) => r.url.endsWith('/api/imdb/search') && r.params.get('q') === 'matrix');
    expect(req.request.method).toBe('GET');
    req.flush([{ imdbId: 'tt0133093', name: 'The Matrix', year: 1999, onWatchlist: false }]);

    expect(received).toEqual([{ imdbId: imdbId('tt0133093'), name: 'The Matrix', year: releaseYear(1999), onWatchlist: false }]);
  });
});
