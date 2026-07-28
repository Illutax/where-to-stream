import { provideHttpClient, withFetch } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { UserPrefsApi } from './user-prefs-api';

describe('UserPrefsApi', () => {
  let api: UserPrefsApi;
  let httpMock: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(withFetch()), provideHttpClientTesting()],
    });
    api = TestBed.inject(UserPrefsApi);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpMock.verify());

  it('PUTs the theme to "../api/me/theme"', () => {
    let completed = false;
    api.setTheme('DARK').subscribe(() => (completed = true));
    const req = httpMock.expectOne((r) => r.url.endsWith('/api/me/theme'));
    expect(req.request.method).toBe('PUT');
    expect(req.request.body).toEqual({ theme: 'DARK' });
    req.flush(null);
    expect(completed).toBe(true);
  });

  it('PUTs showAgeRatings to "../api/me/show-age-ratings"', () => {
    let completed = false;
    api.setShowAgeRatings(false).subscribe(() => (completed = true));
    const req = httpMock.expectOne((r) => r.url.endsWith('/api/me/show-age-ratings'));
    expect(req.request.body).toEqual({ showAgeRatings: false });
    req.flush(null);
    expect(completed).toBe(true);
  });

  it('PUTs the language to "../api/me/language"', () => {
    let completed = false;
    api.setLanguage('DE').subscribe(() => (completed = true));
    const req = httpMock.expectOne((r) => r.url.endsWith('/api/me/language'));
    expect(req.request.body).toEqual({ language: 'DE' });
    req.flush(null);
    expect(completed).toBe(true);
  });

  it('PUTs showGermanTitle to "../api/me/show-german-title"', () => {
    let completed = false;
    api.setShowGermanTitle(true).subscribe(() => (completed = true));
    const req = httpMock.expectOne((r) => r.url.endsWith('/api/me/show-german-title'));
    expect(req.request.body).toEqual({ showGermanTitle: true });
    req.flush(null);
    expect(completed).toBe(true);
  });

  it('PUTs the view mode to "../api/me/view-mode"', () => {
    let completed = false;
    api.setViewMode('LIST').subscribe(() => (completed = true));
    const req = httpMock.expectOne((r) => r.url.endsWith('/api/me/view-mode'));
    expect(req.request.body).toEqual({ viewMode: 'LIST' });
    req.flush(null);
    expect(completed).toBe(true);
  });

  it('PUTs tilesPerRow to "../api/me/tiles-per-row"', () => {
    let completed = false;
    api.setTilesPerRow(3).subscribe(() => (completed = true));
    const req = httpMock.expectOne((r) => r.url.endsWith('/api/me/tiles-per-row'));
    expect(req.request.body).toEqual({ tilesPerRow: 3 });
    req.flush(null);
    expect(completed).toBe(true);
  });
});
