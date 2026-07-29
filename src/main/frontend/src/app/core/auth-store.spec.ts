import { provideHttpClient, withFetch } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { AuthStore } from './auth-store';

describe('AuthStore', () => {
  let store: AuthStore;
  let httpMock: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(withFetch()), provideHttpClientTesting()],
    });
    store = TestBed.inject(AuthStore);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpMock.verify());

  it('is empty before loading', () => {
    expect(store.me()).toBeNull();
    expect(store.isAdmin()).toBe(false);
    expect(store.username()).toBeNull();
  });

  it('load() populates the principal and derived signals', () => {
    store.load();
    httpMock.expectOne((r) => r.url.endsWith('/api/me')).flush({
      authenticated: true,
      username: 'alice',
      roles: ['ADMIN', 'USER'],
      admin: true,
      theme: 'DARK',
      tmdbAttribution: true,
    });

    expect(store.username()).toBe('alice');
    expect(store.isAdmin()).toBe(true);
    expect(store.authenticated()).toBe(true);
    expect(store.tmdbAttribution()).toBe(true);
  });

  it('tmdbAttribution defaults to false when the source is IMDb (flag absent/false)', () => {
    store.load();
    httpMock.expectOne((r) => r.url.endsWith('/api/me')).flush({
      authenticated: true,
      username: 'bob',
      roles: ['USER'],
      admin: false,
      theme: 'SYSTEM',
      tmdbAttribution: false,
    });

    expect(store.tmdbAttribution()).toBe(false);
  });

  it('load() failure leaves the store empty', () => {
    store.load();
    httpMock.expectOne((r) => r.url.endsWith('/api/me')).flush('nope', { status: 401, statusText: 'Unauthorized' });

    expect(store.me()).toBeNull();
    expect(store.isAdmin()).toBe(false);
  });

  /**
   * load() also returns the (shared) result so a caller can subscribe directly for a one-shot
   * "once the principal loads" action instead of watching `me()` via effect() — see ADR-0013.
   * Subscribing must not re-issue the HTTP request (shareReplay), and the emitted value must be
   * the same principal the signal was updated with.
   */
  it('load() returns a shared observable of the loaded principal, without a second request', () => {
    let emitted: unknown;
    store.load().subscribe((me) => (emitted = me));
    httpMock.expectOne((r) => r.url.endsWith('/api/me')).flush({
      authenticated: true,
      username: 'alice',
      roles: ['USER'],
      admin: false,
      theme: 'SYSTEM',
      tmdbAttribution: false,
    });

    expect((emitted as { username: string }).username).toBe('alice');
    httpMock.expectNone(() => true); // the subscribe above must not trigger a second GET
  });

  it('load() returns null on failure instead of erroring, so a subscriber does not need its own error handler', () => {
    let emitted: unknown = 'never set';
    store.load().subscribe((me) => (emitted = me));
    httpMock.expectOne((r) => r.url.endsWith('/api/me')).flush('nope', { status: 401, statusText: 'Unauthorized' });

    expect(emitted).toBeNull();
  });
});
