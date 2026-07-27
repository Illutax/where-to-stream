import { translocoTesting } from '../../testing/transloco-testing';
import { provideHttpClient, withFetch } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { TestbedHarnessEnvironment } from '@angular/cdk/testing/testbed';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MatSortHarness } from '@angular/material/sort/testing';
import { PaidTable } from './paid-table';
import { imdbId, watchlistDate } from '../../core/domain';
import { PaidEntry } from '../../core/models';

describe('PaidTable', () => {
  let fixture: ComponentFixture<PaidTable>;

  const entry = (over: Partial<PaidEntry>): PaidEntry => ({
    name: 'Movie',
    imdbId: imdbId('tt1'),
    price: 'kaufen: HD: 9,99 ',
    added: watchlistDate('2020-01-01'),
    isRated: false,
    year: '2020',
    languages: null,
    ...over,
  });

  beforeEach(() => {
    TestBed.configureTestingModule({ imports: [PaidTable, translocoTesting()], providers: [provideHttpClient(withFetch()), provideHttpClientTesting()] });
    fixture = TestBed.createComponent(PaidTable);
  });

  const rows = () => Array.from(fixture.nativeElement.querySelectorAll('tbody tr')) as HTMLTableRowElement[];

  it('renders the price and languages', () => {
    fixture.componentRef.setInput('entries', [entry({ price: 'kaufen: SD: 4,99 ', languages: 'Deutsch' })]);
    fixture.detectChanges();

    expect(rows()).toHaveLength(1);
    expect(rows()[0].textContent).toContain('kaufen: SD: 4,99');
    expect(rows()[0].textContent).toContain('Deutsch');
  });

  it('renders an empty-state row when there are no entries', () => {
    fixture.componentRef.setInput('entries', []);
    fixture.detectChanges();

    expect(rows()).toHaveLength(1);
    expect(rows()[0].textContent).toContain('Nothing here');
  });

  it('emits a seen toggle when the ✅/⭕ is clicked', () => {
    fixture.componentRef.setInput('entries', [entry({ imdbId: imdbId('tt10'), isRated: false })]);
    fixture.detectChanges();

    let emitted: { imdbId: string; seen: boolean } | undefined;
    fixture.componentInstance.seenToggle.subscribe((e) => (emitted = e));
    (fixture.nativeElement.querySelector('tbody .seen-toggle') as HTMLButtonElement).click();

    expect(emitted).toEqual({ imdbId: 'tt10', seen: true });
  });

  it('highlights the recently-changed row', () => {
    fixture.componentRef.setInput('entries', [entry({ imdbId: imdbId('tt10') })]);
    fixture.componentRef.setInput('recentlyChangedId', imdbId('tt10'));
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelectorAll('tr.recently-changed')).toHaveLength(1);
  });

  it('sorts by year with the "Not yet released" placeholder last (ascending)', async () => {
    fixture.componentRef.setInput('entries', [
      entry({ name: 'Upcoming', imdbId: imdbId('tt2'), year: 'Not yet released' }),
      entry({ name: 'Released', imdbId: imdbId('tt1'), year: '2020' }),
    ]);
    fixture.detectChanges();

    const sort = await TestbedHarnessEnvironment.loader(fixture).getHarness(MatSortHarness);
    const [yearHeader] = await sort.getSortHeaders({ label: 'Year' });

    await yearHeader.click();
    fixture.detectChanges();

    const titles = rows().map((r) => r.querySelector('a')?.textContent?.trim());
    expect(titles).toEqual(['Released', 'Upcoming']);
  });
});
