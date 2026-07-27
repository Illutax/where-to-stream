import { provideHttpClient, withFetch } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { GermanTitleStore } from './german-title-store';

describe('GermanTitleStore', () => {
  let store: GermanTitleStore;
  let httpMock: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(withFetch()), provideHttpClientTesting()],
    });
    store = TestBed.inject(GermanTitleStore);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpMock.verify());

  it('defaults to off', () => {
    expect(store.show()).toBe(false);
  });

  it('init() sets the flag without persisting', () => {
    store.init(true);

    expect(store.show()).toBe(true);
    httpMock.expectNone(() => true);
  });

  it('set() applies and persists the flag', () => {
    store.set(true);

    expect(store.show()).toBe(true);
    const req = httpMock.expectOne((r) => r.url.endsWith('/api/me/show-german-title'));
    expect(req.request.body).toEqual({ showGermanTitle: true });
    req.flush(null);
  });
});
