import { provideHttpClient, withFetch } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { ListSelectionStore } from './list-selection-store';

describe('ListSelectionStore', () => {
  let store: ListSelectionStore;
  let httpMock: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(withFetch()), provideHttpClientTesting()],
    });
    store = TestBed.inject(ListSelectionStore);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpMock.verify());

  it('is empty before loading', () => {
    expect(store.current()).toBeNull();
  });

  it('loads the current list from /api/lists', () => {
    store.load();
    httpMock
      .expectOne((r) => r.url.endsWith('/api/lists'))
      .flush({ current: 'my-list.csv', available: ['my-list.csv'] });

    expect(store.current()).toBe('my-list.csv');
  });

  it('set() updates the current list without a request', () => {
    store.set('other.csv');
    expect(store.current()).toBe('other.csv');
    httpMock.expectNone(() => true);
  });
});
