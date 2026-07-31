import { Sort } from '@angular/material/sort';
import { sortManageRows, sortRows } from './table-sort';

describe('sortRows', () => {
  const rows = [
    { isRated: true, name: 'Beta', year: 2001, added: '2020-03-03' },
    { isRated: false, name: 'alpha', year: 1999, added: '2020-01-01' },
    { isRated: true, name: 'Gamma', year: 2010, added: '2020-02-02' },
  ];

  const sort = (active: string, direction: '' | 'asc' | 'desc'): Sort => ({ active, direction });
  const names = (rs: { name: string }[]) => rs.map((r) => r.name);

  it('sorts by the seen flag: not-seen first ascending, seen first descending (stable)', () => {
    expect(names(sortRows(rows, sort('rated', 'asc')))).toEqual(['alpha', 'Beta', 'Gamma']);
    expect(names(sortRows(rows, sort('rated', 'desc')))).toEqual(['Beta', 'Gamma', 'alpha']);
  });

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
      { isRated: false, name: 'Released', year: '2020', added: '2020-01-01' },
      { isRated: false, name: 'Upcoming', year: 'Not yet released', added: '2020-01-01' },
    ];
    expect(names(sortRows(paid, sort('year', 'asc')))).toEqual(['Released', 'Upcoming']);
    expect(names(sortRows(paid, sort('year', 'desc')))).toEqual(['Upcoming', 'Released']);
  });
});

describe('sortManageRows', () => {
  const rows = [
    { name: 'Beta', needsScrape: false, lastScrapedAt: '2020-03-03T00:00:00Z' },
    { name: 'alpha', needsScrape: true, lastScrapedAt: null },
    { name: 'Gamma', needsScrape: false, lastScrapedAt: '2020-01-01T00:00:00Z' },
  ];

  const sort = (active: string, direction: '' | 'asc' | 'desc'): Sort => ({ active, direction });
  const names = (rs: { name: string }[]) => rs.map((r) => r.name);

  it('sorts by title ascending and descending, case-insensitively', () => {
    expect(names(sortManageRows(rows, sort('title', 'asc')))).toEqual(['alpha', 'Beta', 'Gamma']);
    expect(names(sortManageRows(rows, sort('title', 'desc')))).toEqual(['Gamma', 'Beta', 'alpha']);
  });

  it('sorts a never-scraped row (needsScrape, null date) as the earliest possible timestamp', () => {
    expect(names(sortManageRows(rows, sort('lastScrapedAt', 'asc')))).toEqual(['alpha', 'Gamma', 'Beta']);
    expect(names(sortManageRows(rows, sort('lastScrapedAt', 'desc')))).toEqual(['Beta', 'Gamma', 'alpha']);
  });

  it('sorts an invalidated row (needsScrape) as the earliest timestamp even if its stale lastScrapedAt is the most recent of all', () => {
    // Regression for a reported bug: an invalidated row still carries its old (real) lastScrapedAt,
    // but the "needs scrape" pill hides that date — sorting by the hidden stale value scattered it
    // among unrelated fresh rows instead of grouping it with the rows that need attention.
    const withStaleInvalidated = [
      ...rows,
      { name: 'Delta', needsScrape: true, lastScrapedAt: '2020-06-01T00:00:00Z' }, // newest date of all, but invalidated
    ];

    expect(names(sortManageRows(withStaleInvalidated, sort('lastScrapedAt', 'asc'))))
      .toEqual(['alpha', 'Delta', 'Gamma', 'Beta']);
    expect(names(sortManageRows(withStaleInvalidated, sort('lastScrapedAt', 'desc'))))
      .toEqual(['Beta', 'Gamma', 'alpha', 'Delta']);
  });

  it('restores the input order when the direction is empty', () => {
    expect(names(sortManageRows(rows, sort('title', '')))).toEqual(['Beta', 'alpha', 'Gamma']);
  });

  it('does not mutate the input array', () => {
    const original = [...rows];
    sortManageRows(rows, sort('title', 'asc'));
    expect(rows).toEqual(original);
  });
});
