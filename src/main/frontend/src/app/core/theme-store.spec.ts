import { provideHttpClient, withFetch } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { ThemeStore } from './theme-store';

describe('ThemeStore', () => {
  let store: ThemeStore;
  let httpMock: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(withFetch()), provideHttpClientTesting()],
    });
    store = TestBed.inject(ThemeStore);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
    document.documentElement.style.colorScheme = '';
  });

  it('defaults to the SYSTEM theme', () => {
    expect(store.theme()).toBe('SYSTEM');
  });

  it('init() applies the color-scheme without persisting', () => {
    store.init('DARK');

    expect(store.theme()).toBe('DARK');
    expect(document.documentElement.style.colorScheme).toBe('dark');
    httpMock.expectNone(() => true);
  });

  it('set() applies the scheme and persists it', () => {
    store.set('LIGHT');

    expect(store.theme()).toBe('LIGHT');
    expect(document.documentElement.style.colorScheme).toBe('light');

    const req = httpMock.expectOne((r) => r.url.endsWith('/api/me/theme'));
    expect(req.request.method).toBe('PUT');
    expect(req.request.body).toEqual({ theme: 'LIGHT' });
    req.flush(null);
  });

  it('maps SYSTEM to the "light dark" scheme (follow the OS)', () => {
    store.set('SYSTEM');
    expect(document.documentElement.style.colorScheme).toBe('light dark');
    httpMock.expectOne((r) => r.url.endsWith('/api/me/theme')).flush(null);
  });
});
