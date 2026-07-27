import { provideHttpClient, withFetch } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { GridPrefsStore } from './grid-prefs-store';

describe('GridPrefsStore', () => {
  let store: GridPrefsStore;
  let httpMock: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(withFetch()), provideHttpClientTesting()],
    });
    store = TestBed.inject(GridPrefsStore);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpMock.verify());

  it('defaults to the GRID view with 6 tiles per row', () => {
    expect(store.viewMode()).toBe('GRID');
    expect(store.tilesPerRow()).toBe(6);
  });

  it('init() adopts both preferences without persisting', () => {
    store.init('LIST', 3);

    expect(store.viewMode()).toBe('LIST');
    expect(store.tilesPerRow()).toBe(3);
    httpMock.expectNone(() => true);
  });

  it('setViewMode() applies and persists the view mode', () => {
    store.setViewMode('LIST');

    expect(store.viewMode()).toBe('LIST');
    const req = httpMock.expectOne((r) => r.url.endsWith('/api/me/view-mode'));
    expect(req.request.method).toBe('PUT');
    expect(req.request.body).toEqual({ viewMode: 'LIST' });
    req.flush(null);
  });

  it('setTilesPerRow() applies and persists the tiles-per-row count', () => {
    store.setTilesPerRow(4);

    expect(store.tilesPerRow()).toBe(4);
    const req = httpMock.expectOne((r) => r.url.endsWith('/api/me/tiles-per-row'));
    expect(req.request.method).toBe('PUT');
    expect(req.request.body).toEqual({ tilesPerRow: 4 });
    req.flush(null);
  });
});
