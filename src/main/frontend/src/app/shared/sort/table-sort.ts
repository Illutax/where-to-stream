import { Sort } from '@angular/material/sort';

/** Row shape the availability tables share for sorting (title/year/added). */
interface SortableRow {
  name: string;
  year: number | string;
  added: string;
}

/**
 * Returns a new array of `rows` ordered by the active Material sort column and direction. The
 * sortable columns are `title` (by name), `year` and `added`. An empty direction — the third
 * click on a header — restores the original input order. Non-mutating and stable.
 */
export function sortRows<T extends SortableRow>(rows: readonly T[], sort: Sort): T[] {
  const copy = [...rows];
  if (!sort.direction || !sort.active) {
    return copy;
  }
  const factor = sort.direction === 'asc' ? 1 : -1;
  return copy.sort((a, b) => factor * compare(a, b, sort.active));
}

function compare(a: SortableRow, b: SortableRow, column: string): number {
  switch (column) {
    case 'title':
      return a.name.localeCompare(b.name);
    case 'year':
      return yearValue(a.year) - yearValue(b.year);
    case 'added':
      // IMDb "added" dates are ISO-style (YYYY-MM-DD), so a plain string compare is chronological.
      return a.added.localeCompare(b.added);
    default:
      return 0;
  }
}

/** Numeric year; the paid table's "Not yet released" placeholder sorts after real years. */
function yearValue(year: number | string): number {
  const n = typeof year === 'number' ? year : parseInt(year, 10);
  return Number.isNaN(n) ? Number.POSITIVE_INFINITY : n;
}
