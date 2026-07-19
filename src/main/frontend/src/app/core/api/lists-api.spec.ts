import { provideHttpClient, withFetch } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { ListsApi } from './lists-api';

describe('ListsApi', () => {
  let api: ListsApi;
  let httpMock: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(withFetch()), provideHttpClientTesting()],
    });
    api = TestBed.inject(ListsApi);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpMock.verify());

  it('GETs the current selection', () => {
    let result: unknown;
    api.getLists().subscribe((s) => (result = s));

    const req = httpMock.expectOne((r) => r.url.endsWith('/api/lists'));
    expect(req.request.method).toBe('GET');
    req.flush({ current: 'a.csv', available: ['a.csv'] });

    expect(result).toEqual({ current: 'a.csv', available: ['a.csv'] });
  });

  it('PUTs the new list name to /api/lists/selection', () => {
    api.changeList('b.csv').subscribe();

    const req = httpMock.expectOne((r) => r.url.endsWith('/api/lists/selection'));
    expect(req.request.method).toBe('PUT');
    expect(req.request.body).toEqual({ name: 'b.csv' });
    req.flush({ selected: 'b.csv', cached: 1 });
  });
});
