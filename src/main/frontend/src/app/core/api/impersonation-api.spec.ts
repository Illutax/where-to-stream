import { provideHttpClient, withFetch } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { ImpersonationApi } from './impersonation-api';

describe('ImpersonationApi', () => {
  let api: ImpersonationApi;
  let httpMock: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(withFetch()), provideHttpClientTesting()],
    });
    api = TestBed.inject(ImpersonationApi);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpMock.verify());

  it('starts an impersonation through the admin endpoint', () => {
    api.switchTo('bob').subscribe();

    const req = httpMock.expectOne((r) => r.urlWithParams.includes('/api/admin/impersonate'));
    expect(req.request.urlWithParams).toContain('username=bob');
    req.flush(null);
  });

  it('escapes a username that would otherwise break the query string', () => {
    api.switchTo('a b&c').subscribe();

    const req = httpMock.expectOne((r) => r.url.includes('/api/admin/impersonate'));
    expect(req.request.url).toContain('username=a%20b%26c');
    req.flush(null);
  });

  it('leaves through the non-admin endpoint', () => {
    api.exit().subscribe();

    // The two paths differ on purpose: a switched session is no longer an admin session.
    httpMock.expectOne((r) => r.url.endsWith('/api/impersonate/exit')).flush(null);
  });
});
