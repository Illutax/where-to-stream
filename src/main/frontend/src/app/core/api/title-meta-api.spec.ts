import { provideHttpClient, withFetch } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { imdbId } from '../domain';
import { TitleMetaApi } from './title-meta-api';

describe('TitleMetaApi', () => {
  let api: TitleMetaApi;
  let httpMock: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(withFetch()), provideHttpClientTesting()],
    });
    api = TestBed.inject(TitleMetaApi);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpMock.verify());

  it('GETs "../api/titles/{imdbId}/meta"', () => {
    let received: unknown;
    api.get(imdbId('tt1')).subscribe((m) => (received = m));

    const req = httpMock.expectOne((r) => r.url.endsWith('/api/titles/tt1/meta'));
    expect(req.request.method).toBe('GET');
    req.flush({ rating: { system: 'FSK', label: '12' }, germanTitle: 'Titel' });

    expect(received).toEqual({ rating: { system: 'FSK', label: '12' }, germanTitle: 'Titel' });
  });
});
