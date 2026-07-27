import { provideHttpClient, withFetch } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { GridPrefsApi } from './grid-prefs-api';

describe('GridPrefsApi', () => {
  let api: GridPrefsApi;
  let httpMock: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(withFetch()), provideHttpClientTesting()],
    });
    api = TestBed.inject(GridPrefsApi);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpMock.verify());

  it('PUTs the chosen view mode to "../api/me/view-mode"', () => {
    let completed = false;
    api.setViewMode('LIST').subscribe(() => (completed = true));

    const req = httpMock.expectOne((r) => r.url.endsWith('/api/me/view-mode'));
    expect(req.request.method).toBe('PUT');
    expect(req.request.body).toEqual({ viewMode: 'LIST' });
    req.flush(null);

    expect(completed).toBe(true);
  });

  it('PUTs the chosen tiles-per-row to "../api/me/tiles-per-row"', () => {
    let completed = false;
    api.setTilesPerRow(3).subscribe(() => (completed = true));

    const req = httpMock.expectOne((r) => r.url.endsWith('/api/me/tiles-per-row'));
    expect(req.request.method).toBe('PUT');
    expect(req.request.body).toEqual({ tilesPerRow: 3 });
    req.flush(null);

    expect(completed).toBe(true);
  });
});
