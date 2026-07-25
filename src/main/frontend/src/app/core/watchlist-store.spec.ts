import { provideHttpClient, withFetch } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { WatchlistStore } from './watchlist-store';

describe('WatchlistStore', () => {
  let store: WatchlistStore;
  let httpMock: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(withFetch()), provideHttpClientTesting()],
    });
    store = TestBed.inject(WatchlistStore);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpMock.verify());

  it('is empty before loading', () => {
    expect(store.count()).toBeNull();
  });

  it('loads the count from /api/watchlist', () => {
    store.load();
    httpMock
      .expectOne((r) => r.url.endsWith('/api/watchlist'))
      .flush({ count: 12, lastImportedAt: null });

    expect(store.count()).toBe(12);
  });

  it('set() updates the count without a request', () => {
    store.set(3);
    expect(store.count()).toBe(3);
    httpMock.expectNone(() => true);
  });
});
