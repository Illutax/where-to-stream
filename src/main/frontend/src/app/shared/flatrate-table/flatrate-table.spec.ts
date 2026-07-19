import { ComponentFixture, TestBed } from '@angular/core/testing';
import { FlatrateTable } from './flatrate-table';
import { FlatrateEntry } from '../../core/models';

describe('FlatrateTable', () => {
  let fixture: ComponentFixture<FlatrateTable>;

  const entry = (over: Partial<FlatrateEntry>): FlatrateEntry => ({
    isRated: false,
    name: 'Movie',
    imdbId: 'tt1',
    year: 2020,
    added: '2020-01-01',
    ...over,
  });

  beforeEach(() => {
    TestBed.configureTestingModule({ imports: [FlatrateTable] });
    fixture = TestBed.createComponent(FlatrateTable);
  });

  const rows = () => Array.from(fixture.nativeElement.querySelectorAll('tbody tr')) as HTMLTableRowElement[];

  it('renders one row per entry with an IMDb link', () => {
    fixture.componentRef.setInput('entries', [entry({ name: 'Alpha', imdbId: 'tt10' }), entry({ name: 'Beta' })]);
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
});
