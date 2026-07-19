import { provideHttpClient, withFetch } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { ProviderApi } from './provider-api';

describe('ProviderApi', () => {
  let api: ProviderApi;
  let httpMock: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(withFetch()), provideHttpClientTesting()],
    });
    api = TestBed.inject(ProviderApi);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpMock.verify());

  it('GETs the page for the given provider key', () => {
    api.getProvider('netflix').subscribe();
    const req = httpMock.expectOne((r) => r.url.endsWith('/api/providers/netflix'));
    expect(req.request.method).toBe('GET');
    req.flush({ provider: 'netflix', included: [], paid: [] });
  });
});
