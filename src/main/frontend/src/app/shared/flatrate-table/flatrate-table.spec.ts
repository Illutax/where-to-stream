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
    TestBed.configureTestingModule({ imports: [FlatrateTable] });
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
