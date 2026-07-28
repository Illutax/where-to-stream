import { provideHttpClient, withFetch } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { UserPrefsStore } from './user-prefs-store';

describe('UserPrefsStore', () => {
  let store: UserPrefsStore;
  let httpMock: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(withFetch()), provideHttpClientTesting()],
    });
    store = TestBed.inject(UserPrefsStore);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
    document.documentElement.style.colorScheme = '';
  });

  it('defaults to SYSTEM/on/EN/off/GRID/6', () => {
    expect(store.theme()).toBe('SYSTEM');
    expect(store.showAgeRatings()).toBe(true);
    expect(store.language()).toBe('EN');
    expect(store.showGermanTitle()).toBe(false);
    expect(store.viewMode()).toBe('GRID');
    expect(store.tilesPerRow()).toBe(6);
  });

  it('init() adopts a partial update without persisting, leaving other fields untouched', () => {
    store.init({ showAgeRatings: false });

    expect(store.showAgeRatings()).toBe(false);
    expect(store.language()).toBe('EN'); // untouched
    httpMock.expectNone(() => true);
  });

  it('init() adopts a full set of preferences at once (the app-boot case)', () => {
    store.init({
      theme: 'DARK',
      showAgeRatings: false,
      language: 'DE',
      showGermanTitle: true,
      viewMode: 'LIST',
      tilesPerRow: 3,
    });

    expect(store.theme()).toBe('DARK');
    expect(store.showAgeRatings()).toBe(false);
    expect(store.language()).toBe('DE');
    expect(store.showGermanTitle()).toBe(true);
    expect(store.viewMode()).toBe('LIST');
    expect(store.tilesPerRow()).toBe(3);
    expect(document.documentElement.style.colorScheme).toBe('dark');
    httpMock.expectNone(() => true);
  });

  it('setTheme() applies the color-scheme and persists it', () => {
    store.setTheme('LIGHT');

    expect(store.theme()).toBe('LIGHT');
    expect(document.documentElement.style.colorScheme).toBe('light');
    const req = httpMock.expectOne((r) => r.url.endsWith('/api/me/theme'));
    expect(req.request.body).toEqual({ theme: 'LIGHT' });
    req.flush(null);
  });

  it('setShowAgeRatings() applies and persists', () => {
    store.setShowAgeRatings(false);

    expect(store.showAgeRatings()).toBe(false);
    httpMock.expectOne((r) => r.url.endsWith('/api/me/show-age-ratings')).flush(null);
  });

  it('setLanguage() applies and persists', () => {
    store.setLanguage('DE');

    expect(store.language()).toBe('DE');
    httpMock.expectOne((r) => r.url.endsWith('/api/me/language')).flush(null);
  });

  it('setShowGermanTitle() applies and persists', () => {
    store.setShowGermanTitle(true);

    expect(store.showGermanTitle()).toBe(true);
    httpMock.expectOne((r) => r.url.endsWith('/api/me/show-german-title')).flush(null);
  });

  it('setViewMode() applies and persists', () => {
    store.setViewMode('LIST');

    expect(store.viewMode()).toBe('LIST');
    httpMock.expectOne((r) => r.url.endsWith('/api/me/view-mode')).flush(null);
  });

  it('setTilesPerRow() applies and persists', () => {
    store.setTilesPerRow(4);

    expect(store.tilesPerRow()).toBe(4);
    httpMock.expectOne((r) => r.url.endsWith('/api/me/tiles-per-row')).flush(null);
  });
});
