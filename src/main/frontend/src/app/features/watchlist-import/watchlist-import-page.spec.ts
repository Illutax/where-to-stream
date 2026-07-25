import { provideHttpClient, withFetch } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { WatchlistImportPage } from './watchlist-import-page';
import { WatchlistStatus } from '../../core/models';

describe('WatchlistImportPage', () => {
  let fixture: ComponentFixture<WatchlistImportPage>;
  let httpMock: HttpTestingController;

  const status = (count = 3, lastImportedAt: string | null = '2026-01-01T00:00:00Z'): WatchlistStatus => ({
    count,
    lastImportedAt,
  });

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [WatchlistImportPage],
      providers: [provideHttpClient(withFetch()), provideHttpClientTesting()],
    });
    fixture = TestBed.createComponent(WatchlistImportPage);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpMock.verify());

  function flushInitialLoad(dto: WatchlistStatus = status()): void {
    httpMock.expectOne((r) => r.url.endsWith('/api/watchlist') && r.method === 'GET').flush(dto);
    fixture.detectChanges();
  }

  function pickFile(name = 'list.csv'): File {
    const component = fixture.componentInstance as unknown as {
      onFilePicked(files: FileList | null): void;
    };
    const file = new File(['some,csv'], name, { type: 'text/csv' });
    component.onFilePicked({ 0: file, length: 1, item: () => file } as unknown as FileList);
    return file;
  }

  it('loads and renders the watchlist status', () => {
    flushInitialLoad(status(5));
    expect(fixture.nativeElement.textContent).toContain('5');
  });

  it('uploads the picked CSV as multipart and reloads afterwards', () => {
    flushInitialLoad(status(0, null));

    const file = pickFile();
    (fixture.componentInstance as unknown as { onImport(e: Event): void }).onImport(new Event('submit'));

    const post = httpMock.expectOne((r) => r.url.endsWith('/api/watchlist/import') && r.method === 'POST');
    expect(post.request.body instanceof FormData).toBe(true);
    expect((post.request.body as FormData).get('file')).toBe(file);
    post.flush({ added: 2, updated: 0, removed: 0, total: 2 });

    // reloads the status afterwards
    httpMock.expectOne((r) => r.url.endsWith('/api/watchlist') && r.method === 'GET').flush(status(2, null));
  });

  it('clears the watchlist and reloads', () => {
    flushInitialLoad(status(4));

    (fixture.componentInstance as unknown as { onClear(): void }).onClear();

    httpMock.expectOne((r) => r.url.endsWith('/api/watchlist') && r.method === 'DELETE').flush(null);
    httpMock.expectOne((r) => r.url.endsWith('/api/watchlist') && r.method === 'GET').flush(status(0, null));
  });

  it('shows an error alert when the initial load fails', () => {
    httpMock
      .expectOne((r) => r.url.endsWith('/api/watchlist'))
      .flush('boom', { status: 500, statusText: 'Server Error' });
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('app-error-alert')).not.toBeNull();
  });
});
