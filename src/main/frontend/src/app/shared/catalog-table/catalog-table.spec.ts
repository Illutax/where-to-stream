import { TestbedHarnessEnvironment } from '@angular/cdk/testing/testbed';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MatSortHarness } from '@angular/material/sort/testing';
import { CatalogTable } from './catalog-table';
import { imdbId, releaseYear, watchlistDate } from '../../core/domain';
import { OverviewEntry } from '../../core/models';

describe('CatalogTable', () => {
  let fixture: ComponentFixture<CatalogTable>;

  const entry = (over: Partial<OverviewEntry>): OverviewEntry => ({
    isRated: false,
    name: 'Movie',
    imdbId: imdbId('tt1'),
    year: releaseYear(2020),
    added: watchlistDate('2020-01-01'),
    services: null,
    ...over,
  });

  beforeEach(() => {
    TestBed.configureTestingModule({ imports: [CatalogTable] });
    fixture = TestBed.createComponent(CatalogTable);
  });

  function rows(): HTMLTableRowElement[] {
    return Array.from(fixture.nativeElement.querySelectorAll('tbody tr'));
  }

  it('renders one row per entry with an IMDb link', () => {
    fixture.componentRef.setInput('entries', [entry({ name: 'Alpha', imdbId: imdbId('tt10') }), entry({ name: 'Beta' })]);
    fixture.detectChanges();

    expect(rows()).toHaveLength(2);
    const link = rows()[0].querySelector('a') as HTMLAnchorElement;
    expect(link.textContent?.trim()).toBe('Alpha');
    expect(link.getAttribute('href')).toBe('https://www.imdb.com/title/tt10');
    // each row shows a poster thumbnail next to the title
    expect(rows()[0].querySelector('app-poster-thumb')).not.toBeNull();
  });

  it('shows the services when present and "N/A" when null', () => {
    fixture.componentRef.setInput('entries', [
      entry({ imdbId: imdbId('tt1'), services: 'Netflix, Disney+' }),
      entry({ imdbId: imdbId('tt2'), services: null }),
    ]);
    fixture.detectChanges();

    expect(rows()[0].textContent).toContain('Netflix, Disney+');
    expect(rows()[1].querySelector('em')?.textContent?.trim()).toBe('N/A');
  });

  it('renders an empty-state row when there are no entries', () => {
    fixture.componentRef.setInput('entries', []);
    fixture.detectChanges();

    expect(rows()).toHaveLength(1);
    expect(rows()[0].textContent).toContain('No entries');
  });

  it('emits a seen toggle to the opposite of the current flag when the ✅/⭕ is clicked', () => {
    fixture.componentRef.setInput('entries', [entry({ imdbId: imdbId('tt10'), isRated: false })]);
    fixture.detectChanges();

    let emitted: { imdbId: string; seen: boolean } | undefined;
    fixture.componentInstance.seenToggle.subscribe((e) => (emitted = e));
    (fixture.nativeElement.querySelector('tbody .seen-toggle') as HTMLButtonElement).click();

    expect(emitted).toEqual({ imdbId: 'tt10', seen: true });
  });

  it('highlights only the recently-changed row', () => {
    fixture.componentRef.setInput('entries', [entry({ imdbId: imdbId('tt1') }), entry({ imdbId: imdbId('tt2') })]);
    fixture.componentRef.setInput('recentlyChangedId', imdbId('tt1'));
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelectorAll('tr.recently-changed')).toHaveLength(1);
  });

  const titles = () => rows().map((r) => r.querySelector('a')?.textContent?.trim());

  it('sorts by title ascending then descending as the header is clicked', async () => {
    fixture.componentRef.setInput('entries', [
      entry({ name: 'Beta', imdbId: imdbId('tt2') }),
      entry({ name: 'Alpha', imdbId: imdbId('tt1') }),
    ]);
    fixture.detectChanges();

    const sort = await TestbedHarnessEnvironment.loader(fixture).getHarness(MatSortHarness);
    const [titleHeader] = await sort.getSortHeaders({ label: 'Title' });

    await titleHeader.click();
    fixture.detectChanges();
    expect(titles()).toEqual(['Alpha', 'Beta']);

    await titleHeader.click();
    fixture.detectChanges();
    expect(titles()).toEqual(['Beta', 'Alpha']);
  });

  it('sorts by year when the Year header is clicked', async () => {
    fixture.componentRef.setInput('entries', [
      entry({ name: 'Newer', imdbId: imdbId('tt2'), year: releaseYear(2010) }),
      entry({ name: 'Older', imdbId: imdbId('tt1'), year: releaseYear(1999) }),
    ]);
    fixture.detectChanges();

    const sort = await TestbedHarnessEnvironment.loader(fixture).getHarness(MatSortHarness);
    const [yearHeader] = await sort.getSortHeaders({ label: 'Year' });

    await yearHeader.click();
    fixture.detectChanges();
    expect(titles()).toEqual(['Older', 'Newer']);
  });

  it('sorts by the seen flag when the Seen header is clicked', async () => {
    fixture.componentRef.setInput('entries', [
      entry({ name: 'Watched', imdbId: imdbId('tt2'), isRated: true }),
      entry({ name: 'Unwatched', imdbId: imdbId('tt1'), isRated: false }),
    ]);
    fixture.detectChanges();

    const sort = await TestbedHarnessEnvironment.loader(fixture).getHarness(MatSortHarness);
    const [seenHeader] = await sort.getSortHeaders({ label: 'Seen' });

    await seenHeader.click();
    fixture.detectChanges();
    expect(titles()).toEqual(['Unwatched', 'Watched']); // ascending: not-seen first
  });
});
