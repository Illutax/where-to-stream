import { provideHttpClient, withFetch } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { LanguageStore } from './language-store';

describe('LanguageStore', () => {
  let store: LanguageStore;
  let httpMock: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(withFetch()), provideHttpClientTesting()],
    });
    store = TestBed.inject(LanguageStore);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpMock.verify());

  it('defaults to English', () => {
    expect(store.language()).toBe('EN');
  });

  it('init() sets the language without persisting', () => {
    store.init('DE');

    expect(store.language()).toBe('DE');
    httpMock.expectNone(() => true);
  });

  it('set() applies and persists the language', () => {
    store.set('DE');

    expect(store.language()).toBe('DE');
    const req = httpMock.expectOne((r) => r.url.endsWith('/api/me/language'));
    expect(req.request.body).toEqual({ language: 'DE' });
    req.flush(null);
  });
});
