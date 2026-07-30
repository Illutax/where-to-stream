import { provideHttpClient, withFetch } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MatDialog } from '@angular/material/dialog';
import { of } from 'rxjs';
import { WatchlistImportPage } from './watchlist-import-page';
import { WatchlistStatus } from '../../core/models';
import { translocoTesting } from '../../testing/transloco-testing';

describe('WatchlistImportPage', () => {
  let fixture: ComponentFixture<WatchlistImportPage>;
  let httpMock: HttpTestingController;
  let dialogOpen: ReturnType<typeof vi.fn>;

  const status = (count = 3, lastImportedAt: string | null = '2026-01-01T00:00:00Z'): WatchlistStatus => ({
    count,
    lastImportedAt,
  });

  beforeEach(() => {
    dialogOpen = vi.fn();
    TestBed.configureTestingModule({
      imports: [WatchlistImportPage, translocoTesting()],
      providers: [
        provideHttpClient(withFetch()),
        provideHttpClientTesting(),
        { provide: MatDialog, useValue: { open: dialogOpen } },
      ],
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

  it('shows the card and form immediately, in a loading/disabled state, until the status resolves', () => {
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('mat-card')).not.toBeNull();
    expect(fixture.nativeElement.querySelector('.skeleton-bar')).not.toBeNull();
    expect(fixture.nativeElement.querySelector('app-loading')).toBeNull();
    expect((fixture.nativeElement.querySelector('input[type=file]') as HTMLInputElement).disabled).toBe(true);
    expect((fixture.nativeElement.querySelector('.watchlist-clear button') as HTMLButtonElement).disabled).toBe(true);

    httpMock.expectOne((r) => r.url.endsWith('/api/watchlist') && r.method === 'GET').flush(status());
  });

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

  it('opens a confirm dialog and removes only watched titles when confirmed', () => {
    dialogOpen.mockReturnValue({ afterClosed: () => of(true) });
    flushInitialLoad(status(4));

    (fixture.componentInstance as unknown as { onRemoveWatched(): void }).onRemoveWatched();

    expect(dialogOpen).toHaveBeenCalled();
    httpMock.expectOne((r) => r.url.endsWith('/api/watchlist/seen') && r.method === 'DELETE').flush(null);
    httpMock.expectOne((r) => r.url.endsWith('/api/watchlist') && r.method === 'GET').flush(status(2, null));
  });

  it('does not remove anything when the confirm dialog is cancelled', () => {
    dialogOpen.mockReturnValue({ afterClosed: () => of(false) });
    flushInitialLoad(status(4));

    (fixture.componentInstance as unknown as { onRemoveWatched(): void }).onRemoveWatched();

    httpMock.expectNone((r) => r.url.endsWith('/api/watchlist/seen'));
  });

  it('shows an error alert when the initial load fails', () => {
    httpMock
      .expectOne((r) => r.url.endsWith('/api/watchlist'))
      .flush('boom', { status: 500, statusText: 'Server Error' });
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('app-error-alert')).not.toBeNull();
  });
});
