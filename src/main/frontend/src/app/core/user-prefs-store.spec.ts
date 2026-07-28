import { provideHttpClient, withFetch } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { Component, effect, inject, signal } from '@angular/core';
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

  /**
   * Regression test for a real bug: app.ts calls init() from inside an effect() that's meant to
   * depend only on the loaded principal. init() used to read this._prefs() (to re-apply the theme),
   * which registered as a dependency of *whichever* reactive context called it — so any later
   * setViewMode()/setTilesPerRow()/... triggered that effect to re-run and call init() again with
   * the original (by then stale) prefs, silently reverting the just-made change a moment later.
   * Reproduced here with the same shape (an effect that calls init() once a "principal" signal
   * resolves), without needing app.ts itself.
   */
  it('a later setX() is not reverted by an effect that once called init()', async () => {
    const principal = signal<{ tilesPerRow: number } | null>(null);

    @Component({ selector: 'app-test-host', template: '' })
    class TestHost {
      readonly userPrefsStore = inject(UserPrefsStore);
      constructor() {
        effect(() => {
          const me = principal();
          if (me) {
            this.userPrefsStore.init(me);
          }
        });
      }
    }

    const fixture = TestBed.createComponent(TestHost);
    fixture.detectChanges();
    principal.set({ tilesPerRow: 6 }); // simulates GET /api/me resolving
    fixture.detectChanges();
    await fixture.whenStable();

    store.setTilesPerRow(3); // the user's own change, right after
    fixture.detectChanges();
    await fixture.whenStable();
    await new Promise((resolve) => setTimeout(resolve, 0)); // let any scheduled effect re-run flush
    fixture.detectChanges();
    await fixture.whenStable();

    expect(store.tilesPerRow()).toBe(3);
    httpMock.expectOne((r) => r.url.endsWith('/api/me/tiles-per-row')).flush(null);
  });
});
