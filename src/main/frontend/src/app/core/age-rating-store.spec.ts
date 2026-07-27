import { provideHttpClient, withFetch } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { AgeRatingStore } from './age-rating-store';

describe('AgeRatingStore', () => {
  let store: AgeRatingStore;
  let httpMock: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(withFetch()), provideHttpClientTesting()],
    });
    store = TestBed.inject(AgeRatingStore);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpMock.verify());

  it('defaults to showing the age ratings', () => {
    expect(store.showAgeRatings()).toBe(true);
  });

  it('init() sets the flag without persisting', () => {
    store.init(false);

    expect(store.showAgeRatings()).toBe(false);
    httpMock.expectNone(() => true);
  });

  it('set() applies the flag and persists it', () => {
    store.set(false);

    expect(store.showAgeRatings()).toBe(false);
    const req = httpMock.expectOne((r) => r.url.endsWith('/api/me/show-age-ratings'));
    expect(req.request.method).toBe('PUT');
    expect(req.request.body).toEqual({ showAgeRatings: false });
    req.flush(null);
  });
});
