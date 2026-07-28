import { provideHttpClient, withFetch } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { imdbId, releaseYear } from '../../core/domain';
import { ImdbSearchResult } from '../../core/models';
import { translocoTesting } from '../../testing/transloco-testing';
import { ImdbSearchResults } from './imdb-search-results';

describe('ImdbSearchResults', () => {
  let fixture: ComponentFixture<ImdbSearchResults>;

  const result = (over: Partial<ImdbSearchResult>): ImdbSearchResult => ({
    imdbId: imdbId('tt1'),
    name: 'The Matrix',
    year: releaseYear(1999),
    onWatchlist: false,
    ...over,
  });

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [ImdbSearchResults, translocoTesting()],
      providers: [provideHttpClient(withFetch()), provideHttpClientTesting()],
    });
    fixture = TestBed.createComponent(ImdbSearchResults);
  });

  const items = () => Array.from(fixture.nativeElement.querySelectorAll('.search-result')) as HTMLButtonElement[];

  it('renders one entry per result with name and year', () => {
    fixture.componentRef.setInput('results', [
      result({ imdbId: imdbId('tt1'), name: 'The Matrix' }),
      result({ imdbId: imdbId('tt2'), name: 'Stalker', year: releaseYear(1979) }),
    ]);
    fixture.detectChanges();

    expect(items()).toHaveLength(2);
    expect(items()[0].textContent).toContain('The Matrix');
    expect(items()[0].textContent).toContain('1999');
    expect(items()[1].textContent).toContain('Stalker');
  });

  it('shows the already-on-watchlist mark only for flagged results', () => {
    fixture.componentRef.setInput('results', [
      result({ imdbId: imdbId('tt1'), onWatchlist: true }),
      result({ imdbId: imdbId('tt2'), onWatchlist: false }),
    ]);
    fixture.detectChanges();

    expect(items()[0].querySelector('.search-result-onwatchlist')).not.toBeNull();
    expect(items()[1].querySelector('.search-result-onwatchlist')).toBeNull();
  });

  it('emits resultSelected with the imdbId when a result is clicked', () => {
    fixture.componentRef.setInput('results', [result({ imdbId: imdbId('tt7') })]);
    fixture.detectChanges();

    let selected: string | undefined;
    fixture.componentInstance.resultSelected.subscribe((id) => (selected = id));
    items()[0].click();

    expect(selected).toBe('tt7');
  });

  it('shows a "no results" message for an empty list', () => {
    fixture.componentRef.setInput('results', []);
    fixture.detectChanges();

    expect(items()).toHaveLength(0);
    expect(fixture.nativeElement.textContent).toContain('No results');
  });
});
