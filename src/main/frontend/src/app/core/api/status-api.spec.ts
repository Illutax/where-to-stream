import { provideHttpClient, withFetch } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { StatusApi } from './status-api';

describe('StatusApi', () => {
  let api: StatusApi;
  let httpMock: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(withFetch()), provideHttpClientTesting()],
    });
    api = TestBed.inject(StatusApi);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpMock.verify());

  it('GETs the status', () => {
    let result: unknown;
    api.getStatus().subscribe((s) => (result = s));
    const req = httpMock.expectOne((r) => r.url.endsWith('/api/status'));
    expect(req.request.method).toBe('GET');
    req.flush({ version: '1.0', serverStart: '2026-01-01T00:00:00Z' });
    expect(result).toEqual({ version: '1.0', serverStart: '2026-01-01T00:00:00Z' });
  });
});
