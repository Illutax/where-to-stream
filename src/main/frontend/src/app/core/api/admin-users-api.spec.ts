import { provideHttpClient, withFetch } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { AdminUsersApi } from './admin-users-api';

describe('AdminUsersApi', () => {
  let api: AdminUsersApi;
  let httpMock: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(withFetch()), provideHttpClientTesting()],
    });
    api = TestBed.inject(AdminUsersApi);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpMock.verify());

  it('GETs the user list', () => {
    api.list().subscribe();
    const req = httpMock.expectOne((r) => r.url.endsWith('/api/admin/users'));
    expect(req.request.method).toBe('GET');
    req.flush([]);
  });

  it('POSTs a new user', () => {
    api.create({ username: 'bob', password: 'pw', email: null, roles: ['USER'] }).subscribe();
    const req = httpMock.expectOne((r) => r.url.endsWith('/api/admin/users'));
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({ username: 'bob', password: 'pw', email: null, roles: ['USER'] });
    req.flush({});
  });

  it('PUTs an update', () => {
    api.update('42', { email: 'x@y', roles: ['USER'], enabled: false }).subscribe();
    const req = httpMock.expectOne((r) => r.url.endsWith('/api/admin/users/42'));
    expect(req.request.method).toBe('PUT');
    expect(req.request.body).toEqual({ email: 'x@y', roles: ['USER'], enabled: false });
    req.flush({});
  });

  it('DELETEs a user', () => {
    api.delete('42').subscribe();
    const req = httpMock.expectOne((r) => r.url.endsWith('/api/admin/users/42'));
    expect(req.request.method).toBe('DELETE');
    req.flush(null);
  });

  it('POSTs a password reset', () => {
    api.resetPassword('42', 'new').subscribe();
    const req = httpMock.expectOne((r) => r.url.endsWith('/api/admin/users/42/password'));
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({ newPassword: 'new' });
    req.flush(null);
  });
});
