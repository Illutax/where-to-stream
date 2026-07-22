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
    httpMock
      .expectOne((r) => r.url.endsWith('/api/me'))
      .flush({ authenticated: true, username: 'alice', roles: ['ADMIN', 'USER'], admin: true });

    expect(store.username()).toBe('alice');
    expect(store.isAdmin()).toBe(true);
    expect(store.authenticated()).toBe(true);
  });

  it('load() failure leaves the store empty', () => {
    store.load();
    httpMock.expectOne((r) => r.url.endsWith('/api/me')).flush('nope', { status: 401, statusText: 'Unauthorized' });

    expect(store.me()).toBeNull();
    expect(store.isAdmin()).toBe(false);
  });
});
