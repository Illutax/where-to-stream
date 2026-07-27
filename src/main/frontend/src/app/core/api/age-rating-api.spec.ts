import { provideHttpClient, withFetch } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { AgeRatingApi } from './age-rating-api';

describe('AgeRatingApi', () => {
  let api: AgeRatingApi;
  let httpMock: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(withFetch()), provideHttpClientTesting()],
    });
    api = TestBed.inject(AgeRatingApi);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpMock.verify());

  it('PUTs the flag to "../api/me/show-age-ratings"', () => {
    let completed = false;
    api.setShowAgeRatings(false).subscribe(() => (completed = true));

    const req = httpMock.expectOne((r) => r.url.endsWith('/api/me/show-age-ratings'));
    expect(req.request.method).toBe('PUT');
    expect(req.request.body).toEqual({ showAgeRatings: false });
    req.flush(null);

    expect(completed).toBe(true);
  });
});
