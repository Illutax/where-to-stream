import { provideHttpClient, withFetch } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MatDialog } from '@angular/material/dialog';
import { of } from 'rxjs';
import { translocoTesting } from '../../testing/transloco-testing';
import { AddToWatchlistDialog } from '../add-to-watchlist-dialog/add-to-watchlist-dialog';
import { ImdbSearchBox } from './imdb-search-box';

describe('ImdbSearchBox', () => {
  let fixture: ComponentFixture<ImdbSearchBox>;
  let httpMock: HttpTestingController;
  let dialogOpen: ReturnType<typeof vi.fn>;

  beforeEach(() => {
    vi.useFakeTimers();
    dialogOpen = vi.fn();
    TestBed.configureTestingModule({
      imports: [ImdbSearchBox, translocoTesting()],
      providers: [
        provideHttpClient(withFetch()),
        provideHttpClientTesting(),
        { provide: MatDialog, useValue: { open: dialogOpen } },
      ],
    });
    fixture = TestBed.createComponent(ImdbSearchBox);
    httpMock = TestBed.inject(HttpTestingController);
    fixture.detectChanges();
  });

  afterEach(() => {
    fixture.destroy();
    httpMock.verify();
    vi.useRealTimers();
  });

  function openSearch(): void {
    (fixture.nativeElement.querySelector('button') as HTMLButtonElement).click();
    fixture.detectChanges();
  }

  function type(value: string): void {
    const input = fixture.nativeElement.querySelector('input') as HTMLInputElement;
    input.value = value;
    input.dispatchEvent(new Event('input'));
    fixture.detectChanges();
  }

  it('does not search below the minimum query length', () => {
    openSearch();
    type('m');
    vi.advanceTimersByTime(1000);

    httpMock.expectNone(() => true);
  });

  it('debounces rapid keystrokes into a single request after 1 second', () => {
    openSearch();
    type('m');
    type('ma');
    type('mat');
    vi.advanceTimersByTime(999);
    httpMock.expectNone(() => true);

    vi.advanceTimersByTime(1);
    const req = httpMock.expectOne((r) => r.url.endsWith('/api/imdb/search') && r.params.get('q') === 'mat');
    req.flush([]);
  });

  it('renders the results returned for the debounced query', () => {
    openSearch();
    type('matrix');
    vi.advanceTimersByTime(1000);

    httpMock
      .expectOne((r) => r.url.endsWith('/api/imdb/search'))
      .flush([{ imdbId: 'tt0133093', name: 'The Matrix', year: 1999, onWatchlist: false }]);
    fixture.detectChanges();

    const result = document.querySelector('.search-result');
    expect(result?.textContent).toContain('The Matrix');
  });

  it('opens the add-to-watchlist dialog with the selected result and patches onWatchlist locally on success', () => {
    dialogOpen.mockReturnValue({ afterClosed: () => of(true) });
    openSearch();
    type('matrix');
    vi.advanceTimersByTime(1000);
    httpMock
      .expectOne((r) => r.url.endsWith('/api/imdb/search'))
      .flush([{ imdbId: 'tt0133093', name: 'The Matrix', year: 1999, onWatchlist: false }]);
    fixture.detectChanges();

    (document.querySelector('.search-result') as HTMLButtonElement).click();

    expect(dialogOpen).toHaveBeenCalledWith(
      AddToWatchlistDialog,
      { data: { imdbId: 'tt0133093', name: 'The Matrix', year: 1999, onWatchlist: false } },
    );
    fixture.detectChanges();
    expect(document.querySelector('.search-result-onwatchlist')).not.toBeNull();
  });

  it('closes and clears the query on Escape', () => {
    openSearch();
    type('matrix');
    fixture.nativeElement.querySelector('input').dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape' }));
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('input')).toBeNull();
  });
});
