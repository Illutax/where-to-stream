import { provideHttpClient, withFetch } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { By } from '@angular/platform-browser';
import { ChangeListPage } from './change-list-page';
import { ListSelectionStore } from '../../core/list-selection-store';
import { ListPicker } from '../../shared/list-picker/list-picker';
import { ListSelection } from '../../core/models';

describe('ChangeListPage', () => {
  let fixture: ComponentFixture<ChangeListPage>;
  let httpMock: HttpTestingController;
  let store: ListSelectionStore;

  const selection: ListSelection = { current: 'a.csv', available: ['a.csv', 'b.csv'] };

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [ChangeListPage],
      providers: [provideHttpClient(withFetch()), provideHttpClientTesting()],
    });
    fixture = TestBed.createComponent(ChangeListPage);
    httpMock = TestBed.inject(HttpTestingController);
    store = TestBed.inject(ListSelectionStore);
  });

  afterEach(() => httpMock.verify());

  function flushInitialLoad(): void {
    httpMock.expectOne((r) => r.url.endsWith('/api/lists') && r.method === 'GET').flush(selection);
    fixture.detectChanges();
  }

  const picker = () => fixture.debugElement.query(By.directive(ListPicker)).componentInstance as ListPicker;

  it('loads the lists and renders the picker', () => {
    flushInitialLoad();
    expect(fixture.debugElement.query(By.directive(ListPicker))).not.toBeNull();
  });

  it('PUTs the chosen list, updates the store and reloads', () => {
    flushInitialLoad();

    picker().change.emit('b.csv');

    const put = httpMock.expectOne((r) => r.url.endsWith('/api/lists/selection') && r.method === 'PUT');
    expect(put.request.body).toEqual({ name: 'b.csv' });
    put.flush({ selected: 'b.csv', cached: 7 });

    // navbar store reflects the new list
    expect(store.current()).toBe('b.csv');
    // reloads the selection
    httpMock.expectOne((r) => r.url.endsWith('/api/lists') && r.method === 'GET').flush({ current: 'b.csv', available: ['a.csv', 'b.csv'] });
  });

  it('surfaces the ProblemDetail message when switching fails', () => {
    flushInitialLoad();

    picker().change.emit('bad.csv');

    httpMock
      .expectOne((r) => r.url.endsWith('/api/lists/selection'))
      .flush({ detail: 'Unknown list: bad.csv' }, { status: 400, statusText: 'Bad Request' });
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('app-error-alert').textContent).toContain('Unknown list: bad.csv');
    expect(store.current()).toBeNull(); // store not updated on failure
  });
});
