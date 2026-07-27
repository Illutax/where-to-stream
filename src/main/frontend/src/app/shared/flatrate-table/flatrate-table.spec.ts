import { translocoTesting } from '../../testing/transloco-testing';
import { provideHttpClient, withFetch } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { TestbedHarnessEnvironment } from '@angular/cdk/testing/testbed';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MatSortHarness } from '@angular/material/sort/testing';
import { FlatrateTable } from './flatrate-table';
import { imdbId, releaseYear, watchlistDate } from '../../core/domain';
import { FlatrateEntry } from '../../core/models';

describe('FlatrateTable', () => {
  let fixture: ComponentFixture<FlatrateTable>;

  const entry = (over: Partial<FlatrateEntry>): FlatrateEntry => ({
    isRated: false,
    name: 'Movie',
    imdbId: imdbId('tt1'),
    year: releaseYear(2020),
    added: watchlistDate('2020-01-01'),
    ...over,
  });

  beforeEach(() => {
    TestBed.configureTestingModule({ imports: [FlatrateTable, translocoTesting()], providers: [provideHttpClient(withFetch()), provideHttpClientTesting()] });
    fixture = TestBed.createComponent(FlatrateTable);
  });

  const rows = () => Array.from(fixture.nativeElement.querySelectorAll('tbody tr')) as HTMLTableRowElement[];

  it('renders one row per entry with an IMDb link', () => {
    fixture.componentRef.setInput('entries', [entry({ name: 'Alpha', imdbId: imdbId('tt10') }), entry({ name: 'Beta' })]);
    fixture.detectChanges();

    expect(rows()).toHaveLength(2);
    expect((rows()[0].querySelector('a') as HTMLAnchorElement).getAttribute('href'))
      .toBe('https://www.imdb.com/title/tt10');
  });

  it('renders an empty-state row when there are no entries', () => {
    fixture.componentRef.setInput('entries', []);
    fixture.detectChanges();

    expect(rows()).toHaveLength(1);
    expect(rows()[0].textContent).toContain('Nothing here');
  });

  it('emits a seen toggle when the ✅/⭕ is clicked', () => {
    fixture.componentRef.setInput('entries', [entry({ imdbId: imdbId('tt10'), isRated: true })]);
    fixture.detectChanges();

    let emitted: { imdbId: string; seen: boolean } | undefined;
    fixture.componentInstance.seenToggle.subscribe((e) => (emitted = e));
    (fixture.nativeElement.querySelector('tbody .seen-toggle') as HTMLButtonElement).click();

    expect(emitted).toEqual({ imdbId: 'tt10', seen: false });
  });

  it('highlights only the recently-changed row', () => {
    fixture.componentRef.setInput('entries', [entry({ imdbId: imdbId('tt1') }), entry({ imdbId: imdbId('tt2') })]);
    fixture.componentRef.setInput('recentlyChangedId', imdbId('tt2'));
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelectorAll('tr.recently-changed')).toHaveLength(1);
  });

  it('sorts by the "Added" date when its header is clicked', async () => {
    fixture.componentRef.setInput('entries', [
      entry({ name: 'Later', imdbId: imdbId('tt2'), added: watchlistDate('2021-05-05') }),
      entry({ name: 'Earlier', imdbId: imdbId('tt1'), added: watchlistDate('2020-01-01') }),
    ]);
    fixture.detectChanges();

    const sort = await TestbedHarnessEnvironment.loader(fixture).getHarness(MatSortHarness);
    const [addedHeader] = await sort.getSortHeaders({ label: 'Added' });

    await addedHeader.click();
    fixture.detectChanges();

    const titles = rows().map((r) => r.querySelector('a')?.textContent?.trim());
    expect(titles).toEqual(['Earlier', 'Later']);
  });
});
