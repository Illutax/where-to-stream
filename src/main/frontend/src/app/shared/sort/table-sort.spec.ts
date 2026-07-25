import { Sort } from '@angular/material/sort';
import { sortRows } from './table-sort';

describe('sortRows', () => {
  const rows = [
    { name: 'Beta', year: 2001, added: '2020-03-03' },
    { name: 'alpha', year: 1999, added: '2020-01-01' },
    { name: 'Gamma', year: 2010, added: '2020-02-02' },
  ];

  const sort = (active: string, direction: '' | 'asc' | 'desc'): Sort => ({ active, direction });
  const names = (rs: { name: string }[]) => rs.map((r) => r.name);

  it('sorts by title ascending, case-insensitively', () => {
    expect(names(sortRows(rows, sort('title', 'asc')))).toEqual(['alpha', 'Beta', 'Gamma']);
  });

  it('sorts by title descending', () => {
    expect(names(sortRows(rows, sort('title', 'desc')))).toEqual(['Gamma', 'Beta', 'alpha']);
  });

  it('sorts by year numerically both ways', () => {
    expect(names(sortRows(rows, sort('year', 'asc')))).toEqual(['alpha', 'Beta', 'Gamma']);
    expect(names(sortRows(rows, sort('year', 'desc')))).toEqual(['Gamma', 'Beta', 'alpha']);
  });

  it('sorts by added date chronologically', () => {
    expect(names(sortRows(rows, sort('added', 'asc')))).toEqual(['alpha', 'Gamma', 'Beta']);
  });

  it('restores the input order when the direction is empty', () => {
    expect(names(sortRows(rows, sort('title', '')))).toEqual(['Beta', 'alpha', 'Gamma']);
  });

  it('does not mutate the input array', () => {
    const original = [...rows];
    sortRows(rows, sort('title', 'asc'));
    expect(rows).toEqual(original);
  });

  it('treats a non-numeric year (e.g. "Not yet released") as sorting after real years ascending', () => {
    const paid = [
      { name: 'Released', year: '2020', added: '2020-01-01' },
      { name: 'Upcoming', year: 'Not yet released', added: '2020-01-01' },
    ];
    expect(names(sortRows(paid, sort('year', 'asc')))).toEqual(['Released', 'Upcoming']);
    expect(names(sortRows(paid, sort('year', 'desc')))).toEqual(['Upcoming', 'Released']);
  });
});
