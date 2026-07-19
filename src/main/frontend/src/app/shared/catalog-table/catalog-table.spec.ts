import { ComponentFixture, TestBed } from '@angular/core/testing';
import { CatalogTable } from './catalog-table';
import { OverviewEntry } from '../../core/models';

describe('CatalogTable', () => {
  let fixture: ComponentFixture<CatalogTable>;

  const entry = (over: Partial<OverviewEntry>): OverviewEntry => ({
    isRated: false,
    name: 'Movie',
    imdbId: 'tt1',
    year: 2020,
    added: '2020-01-01',
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
    fixture.componentRef.setInput('entries', [entry({ name: 'Alpha', imdbId: 'tt10' }), entry({ name: 'Beta' })]);
    fixture.detectChanges();

    expect(rows()).toHaveLength(2);
    const link = rows()[0].querySelector('a') as HTMLAnchorElement;
    expect(link.textContent?.trim()).toBe('Alpha');
    expect(link.getAttribute('href')).toBe('https://www.imdb.com/title/tt10');
  });

  it('shows the services when present and "N/A" when null', () => {
    fixture.componentRef.setInput('entries', [
      entry({ imdbId: 'tt1', services: 'Netflix, Disney+' }),
      entry({ imdbId: 'tt2', services: null }),
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
});
