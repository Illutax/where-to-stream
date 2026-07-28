import { provideHttpClient, withFetch } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MAT_DIALOG_DATA, MatDialogRef } from '@angular/material/dialog';
import { Observable, of, throwError } from 'rxjs';
import { imdbId, releaseYear } from '../../core/domain';
import { UserPrefsStore } from '../../core/user-prefs-store';
import { translocoTesting } from '../../testing/transloco-testing';
import { AddToWatchlistDialog, AddToWatchlistDialogData } from './add-to-watchlist-dialog';

describe('AddToWatchlistDialog', () => {
  let fixture: ComponentFixture<AddToWatchlistDialog>;
  let httpMock: HttpTestingController;
  let dialogRef: { close: ReturnType<typeof vi.fn> };
  let submit: ReturnType<typeof vi.fn> & (() => Observable<void>);

  const baseData = {
    imdbId: imdbId('tt1'),
    name: 'The Matrix',
    year: releaseYear(1999),
    onWatchlist: false,
  };

  function setup(overrides: Partial<AddToWatchlistDialogData> = {}) {
    dialogRef = { close: vi.fn() };
    submit = vi.fn(() => of(undefined)) as typeof submit;
    const data: AddToWatchlistDialogData = { ...baseData, submit, ...overrides };
    TestBed.configureTestingModule({
      imports: [AddToWatchlistDialog, translocoTesting()],
      providers: [
        provideHttpClient(withFetch()),
        provideHttpClientTesting(),
        { provide: MatDialogRef, useValue: dialogRef },
        { provide: MAT_DIALOG_DATA, useValue: data },
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
    TestBed.inject(UserPrefsStore).init({ showGermanTitle: true });
    fixture.detectChanges();

    httpMock.expectOne((r) => r.url.endsWith('/api/titles/tt1/meta')).flush({ rating: null, germanTitle: 'Die Matrix' });
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('h2').textContent?.trim()).toBe('Die Matrix');
  });

  it('shows an Add button that calls the injected submit function and closes with true on success', () => {
    setup({ onWatchlist: false });
    TestBed.inject(UserPrefsStore).init({ showAgeRatings: false, showGermanTitle: false });
    fixture.detectChanges();

    addButton()!.click();

    expect(submit).toHaveBeenCalledTimes(1);
    expect(dialogRef.close).toHaveBeenCalledWith(true);
  });

  it('shows an error and stays open when the submit function fails', () => {
    submit = vi.fn(() => throwError(() => new Error('boom'))) as typeof submit;
    setup({ onWatchlist: false, submit });
    TestBed.inject(UserPrefsStore).init({ showAgeRatings: false, showGermanTitle: false });
    fixture.detectChanges();

    addButton()!.click();
    fixture.detectChanges();

    expect(dialogRef.close).not.toHaveBeenCalled();
    expect(fixture.nativeElement.textContent).toContain('Could not add this title');
  });

  it('shows the already-on-watchlist state instead of an Add button', () => {
    setup({ onWatchlist: true });
    TestBed.inject(UserPrefsStore).init({ showAgeRatings: false, showGermanTitle: false });
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('Already on your watchlist');
    expect(addButton()).toBeUndefined();
  });
});
