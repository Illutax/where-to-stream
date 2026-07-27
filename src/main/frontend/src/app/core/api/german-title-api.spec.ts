import { provideHttpClient, withFetch } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { GermanTitleApi } from './german-title-api';

describe('GermanTitleApi', () => {
  let api: GermanTitleApi;
  let httpMock: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(withFetch()), provideHttpClientTesting()],
    });
    api = TestBed.inject(GermanTitleApi);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpMock.verify());

  it('PUTs the flag to "../api/me/show-german-title"', () => {
    api.setShowGermanTitle(true).subscribe();

    const req = httpMock.expectOne((r) => r.url.endsWith('/api/me/show-german-title'));
    expect(req.request.method).toBe('PUT');
    expect(req.request.body).toEqual({ showGermanTitle: true });
    req.flush(null);
  });
});
