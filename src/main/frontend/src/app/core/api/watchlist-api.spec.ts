import { provideHttpClient, withFetch } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { WatchlistApi } from './watchlist-api';
import { imdbId, releaseYear } from '../domain';
import { WatchlistImportResult, WatchlistStatus } from '../models';

describe('WatchlistApi', () => {
  let api: WatchlistApi;
  let httpMock: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(withFetch()), provideHttpClientTesting()],
    });
    api = TestBed.inject(WatchlistApi);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpMock.verify());

  it('reads the watchlist status from "../api/watchlist"', () => {
    const payload: WatchlistStatus = { count: 7, lastImportedAt: '2026-01-01T00:00:00Z' };

    let received: WatchlistStatus | undefined;
    api.getStatus().subscribe((s) => (received = s));

    const req = httpMock.expectOne((r) => r.url.endsWith('/api/watchlist'));
    expect(req.request.method).toBe('GET');
    req.flush(payload);

    expect(received).toEqual(payload);
  });

  it('posts the CSV as multipart form data and returns the sync result', () => {
    const result: WatchlistImportResult = { added: 2, updated: 1, removed: 3, total: 10 };
    const file = new File(['some,csv'], 'list.csv', { type: 'text/csv' });

    let received: WatchlistImportResult | undefined;
    api.import(file).subscribe((r) => (received = r));

    const req = httpMock.expectOne((r) => r.url.endsWith('/api/watchlist/import'));
    expect(req.request.method).toBe('POST');
    expect(req.request.body instanceof FormData).toBe(true);
    expect((req.request.body as FormData).get('file')).toBe(file);
    req.flush(result);

    expect(received).toEqual(result);
  });

  it('clears the watchlist with a DELETE', () => {
    let completed = false;
    api.clear().subscribe(() => (completed = true));

    const req = httpMock.expectOne((r) => r.url.endsWith('/api/watchlist'));
    expect(req.request.method).toBe('DELETE');
    req.flush(null);

    expect(completed).toBe(true);
  });

  it('removes only watched titles with a DELETE to "watchlist/seen"', () => {
    let completed = false;
    api.clearSeen().subscribe(() => (completed = true));

    const req = httpMock.expectOne((r) => r.url.endsWith('/api/watchlist/seen'));
    expect(req.request.method).toBe('DELETE');
    req.flush(null);

    expect(completed).toBe(true);
  });

  it('adds a single title with a POST to "watchlist/{imdbId}"', () => {
    let completed = false;
    api.addToWatchlist(imdbId('tt1'), 'The Matrix', releaseYear(1999)).subscribe(() => (completed = true));

    const req = httpMock.expectOne((r) => r.url.endsWith('/api/watchlist/tt1'));
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({ name: 'The Matrix', year: 1999 });
    req.flush(null);

    expect(completed).toBe(true);
  });

  it('sets a title\'s seen flag with a PUT to "watchlist/{imdbId}/seen"', () => {
    let completed = false;
    api.setSeen(imdbId('tt1'), true).subscribe(() => (completed = true));

    const req = httpMock.expectOne((r) => r.url.endsWith('/api/watchlist/tt1/seen'));
    expect(req.request.method).toBe('PUT');
    expect(req.request.body).toEqual({ seen: true });
    req.flush(null);

    expect(completed).toBe(true);
  });
});
