import { Sort } from '@angular/material/sort';

/** Row shape the availability tables share for sorting (seen/title/year/added). */
interface SortableRow {
  isRated: boolean;
  name: string;
  year: number;
  added: string;
}

/**
 * Returns a new array of `rows` ordered by the active Material sort column and direction.
 * The sortable columns are `rated` (by the seen flag), `title` (by name), `year` and `added`.
 * An empty direction — the third click on a header — restores the original input order.
 * Non-mutating and stable.
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
    case 'rated':
      // Ascending: not-seen (false) before seen (true).
      return Number(a.isRated) - Number(b.isRated);
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

/**
 * An unreleased title (`ReleaseYear` 0) sorts after every real year, ascending.
 *
 * <p>The two halves of this used to disagree without anyone noticing. The paid table received its
 * year as the display string and fell through `parseInt` to `NaN`, which this function mapped to
 * the end; every other table received the number 0 and sorted it to the *front*. The same column,
 * two orders, depending on which page you were on. It only became visible when the paid table
 * started receiving a number as well (TODO-60).
 *
 * <p>Sorting last is the half worth keeping: "no year yet" is not "the year zero", and a reader
 * scanning by year wants the known ones first.
 */
function yearValue(year: number): number {
  return year > 0 ? year : Number.POSITIVE_INFINITY;
}

/** Row shape the "Cache Verwalten" manage table sorts by (title, last scraped at). */
interface SortableManageRow {
  name: string;
  needsScrape: boolean;
  lastScrapedAt: string | null;
}

/**
 * Same contract as {@link sortRows}, for the manage table's own columns (`title`, `lastScrapedAt`)
 * — a separate, small function rather than folding it into {@link sortRows}'s `SortableRow`, since
 * the manage table has neither a `year` nor an `added` column.
 */
export function sortManageRows<T extends SortableManageRow>(rows: readonly T[], sort: Sort): T[] {
  const copy = [...rows];
  if (!sort.direction || !sort.active) {
    return copy;
  }
  const factor = sort.direction === 'asc' ? 1 : -1;
  return copy.sort((a, b) => factor * compareManage(a, b, sort.active));
}

function compareManage(a: SortableManageRow, b: SortableManageRow, column: string): number {
  switch (column) {
    case 'title':
      return a.name.localeCompare(b.name);
    case 'lastScrapedAt':
      return lastScrapedAtValue(a) - lastScrapedAtValue(b);
    default:
      return 0;
  }
}

/**
 * A row that "needs scrape" (never cached, or invalidated) always sorts as the earliest possible
 * timestamp, regardless of whether it happens to still carry an old `lastScrapedAt` from before it
 * was invalidated — the status pill doesn't show that stale date, so sorting by it would scatter
 * these rows among unrelated fresh ones instead of grouping them at the "needs attention" end.
 */
function lastScrapedAtValue(row: SortableManageRow): number {
  if (row.needsScrape) {
    return Number.NEGATIVE_INFINITY;
  }
  return row.lastScrapedAt === null ? Number.POSITIVE_INFINITY : Date.parse(row.lastScrapedAt);
}
