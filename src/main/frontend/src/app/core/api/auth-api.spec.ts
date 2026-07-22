import { provideHttpClient, withFetch } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { AuthApi } from './auth-api';

describe('AuthApi', () => {
  let api: AuthApi;
  let httpMock: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(withFetch()), provideHttpClientTesting()],
    });
    api = TestBed.inject(AuthApi);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpMock.verify());

  it('GETs the current principal from /api/me', () => {
    let result: unknown;
    api.me().subscribe((me) => (result = me));

    const req = httpMock.expectOne((r) => r.url.endsWith('/api/me'));
    expect(req.request.method).toBe('GET');
    req.flush({ authenticated: true, username: 'alice', roles: ['ADMIN'], admin: true });

    expect(result).toEqual({ authenticated: true, username: 'alice', roles: ['ADMIN'], admin: true });
  });

  it('POSTs to the form-logout endpoint', () => {
    api.logout().subscribe();
    const req = httpMock.expectOne((r) => r.url.endsWith('/logout'));
    expect(req.request.method).toBe('POST');
    req.flush(null);
  });
});
