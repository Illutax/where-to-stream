import { translocoTesting } from '../../testing/transloco-testing';
import { provideHttpClient, withFetch } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { By } from '@angular/platform-browser';
import { OverviewPage } from './overview-page';
import { ImdbId, imdbId, releaseYear, watchlistDate } from '../../core/domain';
import { UserPrefsStore } from '../../core/user-prefs-store';
import { OverviewEntry } from '../../core/models';
import { SeenStore } from '../../core/seen-store';
import { CatalogTable } from '../../shared/catalog-table/catalog-table';
import { TitleGrid } from '../../shared/title-grid/title-grid';

describe('OverviewPage', () => {
  let fixture: ComponentFixture<OverviewPage>;
  let httpMock: HttpTestingController;
  let toggled: { imdbId: string; seen: boolean } | undefined;

  beforeEach(() => {
    toggled = undefined;
    // Fake store: capture the call and run the optimistic apply immediately (no HTTP/snackbar).
    const seenStore = {
      recentlyChanged: () => null,
      toggle: (id: ImdbId, seen: boolean, apply: (s: boolean) => void) => {
        toggled = { imdbId: id, seen };
        apply(seen);
      },
    };
    TestBed.configureTestingModule({
      imports: [OverviewPage, translocoTesting()],
      providers: [
        provideHttpClient(withFetch()),
        provideHttpClientTesting(),
        { provide: SeenStore, useValue: seenStore },
      ],
    });
    fixture = TestBed.createComponent(OverviewPage); // constructor kicks off the load
    httpMock = TestBed.inject(HttpTestingController);
    // Most existing assertions target the table; the grid (now the default) is covered separately.
    TestBed.inject(UserPrefsStore).init({ viewMode: 'LIST', tilesPerRow: 6 });
  });

  afterEach(() => {
    // The per-row age-rating badges fetch independently; drain those before verifying.
    httpMock.match((r) => r.url.includes('/meta')).forEach((req) => req.flush(null, { status: 404, statusText: 'Not Found' }));
    httpMock.verify();
  });

  it('shows the loading indicator until the catalogue resolves', () => {
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('app-loading')).not.toBeNull();
    httpMock.expectOne((r) => r.url.endsWith('/api/catalog')).flush([]);
  });

  it('renders the catalogue table on success', () => {
    const payload: OverviewEntry[] = [
      { isRated: true, name: 'Movie', imdbId: imdbId('tt1'), year: releaseYear(2020), added: watchlistDate('2020-01-01'), services: 'Netflix' },
    ];
    httpMock.expectOne((r) => r.url.endsWith('/api/catalog')).flush(payload);
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('app-loading')).toBeNull();
    expect(fixture.nativeElement.querySelectorAll('tbody tr')).toHaveLength(1);
    expect(fixture.nativeElement.textContent).toContain('Movie');
  });

  it('delegates a seen toggle to the store and updates the row optimistically', () => {
    const payload: OverviewEntry[] = [
      { isRated: false, name: 'Movie', imdbId: imdbId('tt1'), year: releaseYear(2020), added: watchlistDate('2020-01-01'), services: 'Netflix' },
    ];
    httpMock.expectOne((r) => r.url.endsWith('/api/catalog')).flush(payload);
    fixture.detectChanges();

    const table = fixture.debugElement.query(By.directive(CatalogTable)).componentInstance as CatalogTable;
    table.seenToggle.emit({ imdbId: imdbId('tt1'), seen: true });
    fixture.detectChanges();

    expect(toggled).toEqual({ imdbId: 'tt1', seen: true });
    // optimistic apply flipped the flag -> the toggle now renders ✅
    expect(fixture.nativeElement.querySelector('tbody .seen-toggle').textContent).toContain('✅');
  });

  it('renders the poster grid by default (GRID is the default view mode)', () => {
    TestBed.inject(UserPrefsStore).init({ viewMode: 'GRID', tilesPerRow: 6 });
    const payload: OverviewEntry[] = [
      { isRated: true, name: 'Movie', imdbId: imdbId('tt1'), year: releaseYear(2020), added: watchlistDate('2020-01-01'), services: 'Netflix' },
    ];
    httpMock.expectOne((r) => r.url.endsWith('/api/catalog')).flush(payload);
    fixture.detectChanges();

    expect(fixture.debugElement.query(By.directive(TitleGrid))).not.toBeNull();
    expect(fixture.nativeElement.textContent).toContain('Movie');
  });

  it('delegates a seen toggle from the grid to the store', () => {
    TestBed.inject(UserPrefsStore).init({ viewMode: 'GRID', tilesPerRow: 6 });
    const payload: OverviewEntry[] = [
      { isRated: false, name: 'Movie', imdbId: imdbId('tt1'), year: releaseYear(2020), added: watchlistDate('2020-01-01'), services: 'Netflix' },
    ];
    httpMock.expectOne((r) => r.url.endsWith('/api/catalog')).flush(payload);
    fixture.detectChanges();

    const grid = fixture.debugElement.query(By.directive(TitleGrid)).componentInstance as TitleGrid;
    grid.seenToggle.emit({ imdbId: imdbId('tt1'), seen: true });

    expect(toggled).toEqual({ imdbId: 'tt1', seen: true });
  });

  it('shows an error alert when the request fails', () => {
    httpMock
      .expectOne((r) => r.url.endsWith('/api/catalog'))
      .flush('boom', { status: 500, statusText: 'Server Error' });
    fixture.detectChanges();

    const alert = fixture.nativeElement.querySelector('.error-alert');
    expect(alert).not.toBeNull();
    expect(alert.textContent).toContain('Failed to load the catalogue');
  });
});
