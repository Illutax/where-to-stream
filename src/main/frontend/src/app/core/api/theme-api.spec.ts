import { provideHttpClient, withFetch } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { ThemeApi } from './theme-api';

describe('ThemeApi', () => {
  let api: ThemeApi;
  let httpMock: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(withFetch()), provideHttpClientTesting()],
    });
    api = TestBed.inject(ThemeApi);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpMock.verify());

  it('PUTs the chosen theme to "../api/me/theme"', () => {
    let completed = false;
    api.setTheme('DARK').subscribe(() => (completed = true));

    const req = httpMock.expectOne((r) => r.url.endsWith('/api/me/theme'));
    expect(req.request.method).toBe('PUT');
    expect(req.request.body).toEqual({ theme: 'DARK' });
    req.flush(null);

    expect(completed).toBe(true);
  });
});
