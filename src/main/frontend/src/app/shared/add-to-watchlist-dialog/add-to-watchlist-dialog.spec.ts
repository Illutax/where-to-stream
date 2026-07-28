import { provideHttpClient, withFetch } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MAT_DIALOG_DATA, MatDialogRef } from '@angular/material/dialog';
import { AgeRatingStore } from '../../core/age-rating-store';
import { imdbId, releaseYear } from '../../core/domain';
import { GermanTitleStore } from '../../core/german-title-store';
import { translocoTesting } from '../../testing/transloco-testing';
import { AddToWatchlistDialog, AddToWatchlistDialogData } from './add-to-watchlist-dialog';

describe('AddToWatchlistDialog', () => {
  let fixture: ComponentFixture<AddToWatchlistDialog>;
  let httpMock: HttpTestingController;
  let dialogRef: { close: ReturnType<typeof vi.fn> };

  const data: AddToWatchlistDialogData = {
    imdbId: imdbId('tt1'),
    name: 'The Matrix',
    year: releaseYear(1999),
    onWatchlist: false,
  };

  function setup(overrides: Partial<AddToWatchlistDialogData> = {}) {
    dialogRef = { close: vi.fn() };
    TestBed.configureTestingModule({
      imports: [AddToWatchlistDialog, translocoTesting()],
      providers: [
        provideHttpClient(withFetch()),
        provideHttpClientTesting(),
        { provide: MatDialogRef, useValue: dialogRef },
        { provide: MAT_DIALOG_DATA, useValue: { ...data, ...overrides } },
      ],
    });
    fixture = TestBed.createComponent(AddToWatchlistDialog);
    httpMock = TestBed.inject(HttpTestingController);
  }

  afterEach(() => httpMock.verify());

  const addButton = () =>
    Array.from(fixture.nativeElement.querySelectorAll('button')).find((b) =>
      (b as HTMLButtonElement).textContent?.includes('Add to Watchlist'),
    ) as HTMLButtonElement | undefined;

  it('shows the poster, name, and year, and fetches meta lazily', () => {
    setup();
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('.add-to-watchlist-poster').getAttribute('src')).toContain('/api/titles/tt1/poster/full');
    expect(fixture.nativeElement.textContent).toContain('The Matrix');
    expect(fixture.nativeElement.textContent).toContain('1999');

    httpMock.expectOne((r) => r.url.endsWith('/api/titles/tt1/meta')).flush({ rating: { system: 'FSK', label: '12' }, germanTitle: 'Die Matrix' });
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('.age-badge')?.textContent?.trim()).toBe('12');
  });

  it('shows the German title when that preference is on and one exists', () => {
    setup();
    TestBed.inject(GermanTitleStore).init(true);
    fixture.detectChanges();

    httpMock.expectOne((r) => r.url.endsWith('/api/titles/tt1/meta')).flush({ rating: null, germanTitle: 'Die Matrix' });
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('h2').textContent?.trim()).toBe('Die Matrix');
  });

  it('shows an Add button and adds the title, closing with true on success', () => {
    setup({ onWatchlist: false });
    TestBed.inject(AgeRatingStore).init(false);
    TestBed.inject(GermanTitleStore).init(false);
    fixture.detectChanges();

    addButton()!.click();
    fixture.detectChanges();

    const req = httpMock.expectOne((r) => r.url.endsWith('/api/watchlist/tt1'));
    expect(req.request.body).toEqual({ name: 'The Matrix', year: 1999 });
    req.flush(null);

    expect(dialogRef.close).toHaveBeenCalledWith(true);
  });

  it('shows an error and stays open when adding fails', () => {
    setup({ onWatchlist: false });
    TestBed.inject(AgeRatingStore).init(false);
    TestBed.inject(GermanTitleStore).init(false);
    fixture.detectChanges();

    addButton()!.click();
    httpMock.expectOne((r) => r.url.endsWith('/api/watchlist/tt1')).flush('boom', { status: 409, statusText: 'Conflict' });
    fixture.detectChanges();

    expect(dialogRef.close).not.toHaveBeenCalled();
    expect(fixture.nativeElement.textContent).toContain('Could not add this title');
  });

  it('shows the already-on-watchlist state instead of an Add button', () => {
    setup({ onWatchlist: true });
    TestBed.inject(AgeRatingStore).init(false);
    TestBed.inject(GermanTitleStore).init(false);
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('Already on your watchlist');
    expect(addButton()).toBeUndefined();
  });
});
