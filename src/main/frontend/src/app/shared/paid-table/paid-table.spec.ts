import { ComponentFixture, TestBed } from '@angular/core/testing';
import { PaidTable } from './paid-table';
import { PaidEntry } from '../../core/models';

describe('PaidTable', () => {
  let fixture: ComponentFixture<PaidTable>;

  const entry = (over: Partial<PaidEntry>): PaidEntry => ({
    name: 'Movie',
    imdbId: 'tt1',
    price: 'kaufen: HD: 9,99 ',
    added: '2020-01-01',
    isRated: false,
    year: '2020',
    languages: null,
    ...over,
  });

  beforeEach(() => {
    TestBed.configureTestingModule({ imports: [PaidTable] });
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
});
