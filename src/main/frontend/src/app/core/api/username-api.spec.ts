import { provideHttpClient, withFetch } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { UsernameApi } from './username-api';

describe('UsernameApi', () => {
  let api: UsernameApi;
  let httpMock: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(withFetch()), provideHttpClientTesting()],
    });
    api = TestBed.inject(UsernameApi);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpMock.verify());

  it('PUTs the new username to "../api/me/username"', () => {
    api.setUsername('newname').subscribe();

    const req = httpMock.expectOne((r) => r.url.endsWith('/api/me/username'));
    expect(req.request.method).toBe('PUT');
    expect(req.request.body).toEqual({ username: 'newname' });
    req.flush(null);
  });
});
