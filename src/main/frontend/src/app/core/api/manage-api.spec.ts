import { provideHttpClient, withFetch } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { ManageApi } from './manage-api';

describe('ManageApi', () => {
  let api: ManageApi;
  let httpMock: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(withFetch()), provideHttpClientTesting()],
    });
    api = TestBed.inject(ManageApi);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpMock.verify());

  it('GETs the manage page', () => {
    api.getManagePage().subscribe();
    const req = httpMock.expectOne((r) => r.url.endsWith('/api/manage'));
    expect(req.request.method).toBe('GET');
    req.flush({ rows: [], needsScrapeCount: 0 });
  });

  it('POSTs the ids to invalidate', () => {
    api.invalidate(['tt1', 'tt2']).subscribe();
    const req = httpMock.expectOne((r) => r.url.endsWith('/api/manage/invalidate'));
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({ imdbIds: ['tt1', 'tt2'] });
    req.flush({ invalidated: 2 });
  });

  it('POSTs to trigger a scrape', () => {
    api.scrape().subscribe();
    const req = httpMock.expectOne((r) => r.url.endsWith('/api/manage/scrape'));
    expect(req.request.method).toBe('POST');
    req.flush({ scraped: 0 });
  });
});
