import { provideHttpClient, withFetch } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { LanguageApi } from './language-api';

describe('LanguageApi', () => {
  let api: LanguageApi;
  let httpMock: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(withFetch()), provideHttpClientTesting()],
    });
    api = TestBed.inject(LanguageApi);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpMock.verify());

  it('PUTs the chosen language to "../api/me/language"', () => {
    api.setLanguage('DE').subscribe();

    const req = httpMock.expectOne((r) => r.url.endsWith('/api/me/language'));
    expect(req.request.method).toBe('PUT');
    expect(req.request.body).toEqual({ language: 'DE' });
    req.flush(null);
  });
});
