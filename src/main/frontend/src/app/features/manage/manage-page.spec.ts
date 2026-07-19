import { provideHttpClient, withFetch } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { By } from '@angular/platform-browser';
import { ManagePage } from './manage-page';
import { ManageTable } from '../../shared/manage-table/manage-table';
import { ManagePage as ManagePageDto } from '../../core/models';

describe('ManagePage', () => {
  let fixture: ComponentFixture<ManagePage>;
  let httpMock: HttpTestingController;

  const dto = (needsScrapeCount = 1): ManagePageDto => ({
    rows: [{ imdbId: 'tt1', name: 'Alpha', isRated: true, needsScrape: true }],
    needsScrapeCount,
  });

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [ManagePage],
      providers: [provideHttpClient(withFetch()), provideHttpClientTesting()],
    });
    fixture = TestBed.createComponent(ManagePage);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpMock.verify());

  function flushInitialLoad(): void {
    httpMock.expectOne((r) => r.url.endsWith('/api/manage') && r.method === 'GET').flush(dto());
    fixture.detectChanges();
  }

  const table = () => fixture.debugElement.query(By.directive(ManageTable)).componentInstance as ManageTable;

  it('loads the manage page and renders the table', () => {
    flushInitialLoad();
    expect(fixture.debugElement.query(By.directive(ManageTable))).not.toBeNull();
  });

  it('posts the selected ids on invalidate and then reloads', () => {
    flushInitialLoad();

    table().invalidate.emit(['tt1', 'tt2']);

    const post = httpMock.expectOne((r) => r.url.endsWith('/api/manage/invalidate') && r.method === 'POST');
    expect(post.request.body).toEqual({ imdbIds: ['tt1', 'tt2'] });
    post.flush({ invalidated: 2 });

    // reloads the page afterwards
    httpMock.expectOne((r) => r.url.endsWith('/api/manage') && r.method === 'GET').flush(dto(0));
  });

  it('posts scrape and then reloads', () => {
    flushInitialLoad();

    table().scrape.emit();

    httpMock.expectOne((r) => r.url.endsWith('/api/manage/scrape') && r.method === 'POST').flush({ scraped: 3 });
    httpMock.expectOne((r) => r.url.endsWith('/api/manage') && r.method === 'GET').flush(dto(0));
  });

  it('shows an error alert when the initial load fails', () => {
    httpMock
      .expectOne((r) => r.url.endsWith('/api/manage'))
      .flush('boom', { status: 500, statusText: 'Server Error' });
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('app-error-alert')).not.toBeNull();
  });
});
