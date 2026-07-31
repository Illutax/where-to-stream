import { translocoTesting } from '../../testing/transloco-testing';
import { provideHttpClient, withFetch } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { By } from '@angular/platform-browser';
import { OverviewPage } from './overview-page';
import { ImdbId, imdbId, releaseYear, watchlistDate } from '../../core/domain';
import { UserPrefsStore } from '../../core/user-prefs-store';
import { CatalogPage, OverviewEntry } from '../../core/models';
import { SeenStore } from '../../core/seen-store';
import { CatalogTable } from '../../shared/catalog-table/catalog-table';
import { TitleGrid } from '../../shared/title-grid/title-grid';

describe('OverviewPage', () => {
  let fixture: ComponentFixture<OverviewPage>;
  let httpMock: HttpTestingController;
  let toggled: { imdbId: string; seen: boolean } | undefined;

  const page = (over: Partial<CatalogPage>): CatalogPage => ({
    entries: [],
    hasStaleEntries: false,
    ...over,
  });

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

  it('shows the table header and skeleton rows (not a spinner) until the catalogue resolves', () => {
    fixture.detectChanges();

    // Header/column labels render immediately (they don't depend on the fetch) ...
    expect(fixture.nativeElement.querySelectorAll('th').length).toBeGreaterThan(0);
    expect(fixture.nativeElement.textContent).toContain('Title');
    // ... while the body shows placeholder bars instead of real rows.
    expect(fixture.nativeElement.querySelectorAll('.skeleton-bar').length).toBeGreaterThan(0);
    expect(fixture.nativeElement.querySelector('app-loading')).toBeNull();

    httpMock.expectOne((r) => r.url.endsWith('/api/catalog')).flush(page({}));
  });

  it('shows the grid toolbar and skeleton tiles sized to tilesPerRow until the catalogue resolves', () => {
    TestBed.inject(UserPrefsStore).init({ viewMode: 'GRID', tilesPerRow: 4 });
    fixture.detectChanges();

    // The sort/tiles-per-row toolbar renders immediately (it doesn't depend on the fetch) ...
    expect(fixture.nativeElement.querySelector('.grid-toolbar')).not.toBeNull();
    // ... while the grid itself shows placeholder tiles sized to the user's tilesPerRow.
    const grid = fixture.nativeElement.querySelector('.tile-grid') as HTMLElement;
    expect(grid.style.getPropertyValue('--tiles-per-row')).toBe('4');
    expect(fixture.nativeElement.querySelectorAll('app-title-tile-skeleton').length).toBe(4 * 3);

    httpMock.expectOne((r) => r.url.endsWith('/api/catalog')).flush(page({}));
  });

  it('renders the catalogue table on success', () => {
    const payload: OverviewEntry[] = [
      { isRated: true, name: 'Movie', imdbId: imdbId('tt1'), year: releaseYear(2020), added: watchlistDate('2020-01-01'), services: 'Netflix' },
    ];
    httpMock.expectOne((r) => r.url.endsWith('/api/catalog')).flush(page({ entries: payload }));
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelectorAll('.skeleton-bar')).toHaveLength(0);
    expect(fixture.nativeElement.querySelectorAll('tbody tr')).toHaveLength(1);
    expect(fixture.nativeElement.textContent).toContain('Movie');
  });

  it('delegates a seen toggle to the store and updates the row optimistically', () => {
    const payload: OverviewEntry[] = [
      { isRated: false, name: 'Movie', imdbId: imdbId('tt1'), year: releaseYear(2020), added: watchlistDate('2020-01-01'), services: 'Netflix' },
    ];
    httpMock.expectOne((r) => r.url.endsWith('/api/catalog')).flush(page({ entries: payload }));
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
    httpMock.expectOne((r) => r.url.endsWith('/api/catalog')).flush(page({ entries: payload }));
    fixture.detectChanges();

    expect(fixture.debugElement.query(By.directive(TitleGrid))).not.toBeNull();
    expect(fixture.nativeElement.textContent).toContain('Movie');
  });

  it('delegates a seen toggle from the grid to the store', () => {
    TestBed.inject(UserPrefsStore).init({ viewMode: 'GRID', tilesPerRow: 6 });
    const payload: OverviewEntry[] = [
      { isRated: false, name: 'Movie', imdbId: imdbId('tt1'), year: releaseYear(2020), added: watchlistDate('2020-01-01'), services: 'Netflix' },
    ];
    httpMock.expectOne((r) => r.url.endsWith('/api/catalog')).flush(page({ entries: payload }));
    fixture.detectChanges();

    const grid = fixture.debugElement.query(By.directive(TitleGrid)).componentInstance as TitleGrid;
    grid.seenToggle.emit({ imdbId: imdbId('tt1'), seen: true });

    expect(toggled).toEqual({ imdbId: 'tt1', seen: true });
  });

  it('shows the stale-data banner when the response has stale entries', () => {
    httpMock.expectOne((r) => r.url.endsWith('/api/catalog')).flush(page({ hasStaleEntries: true }));
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('.stale-data-banner')).not.toBeNull();
  });

  it('hides the stale-data banner when nothing is stale', () => {
    httpMock.expectOne((r) => r.url.endsWith('/api/catalog')).flush(page({ hasStaleEntries: false }));
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('.stale-data-banner')).toBeNull();
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
