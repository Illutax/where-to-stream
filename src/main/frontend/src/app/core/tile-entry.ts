import { ImdbId, ReleaseYear, releaseYearDisplay, WatchlistDate } from './domain';
import { FlatrateEntry, OverviewEntry, PaidEntry } from './models';

/**
 * The common row shape the poster-tile grid renders — normalized from the three differing table
 * row DTOs (which carry columns the grid doesn't show: services / price / languages).
 */
export interface TileEntry {
  imdbId: ImdbId;
  name: string;
  year: string;
  /**
   * The same year as a number, or null where the source row only ever had it as text.
   * Needed by the eBay search link (TODO-57), which has to tell an unreleased title apart from a
   * released one without re-reading a display string.
   */
  releaseYear: ReleaseYear | null;
  added: WatchlistDate;
  isRated: boolean;
}

export function overviewToTile(e: OverviewEntry): TileEntry {
  return { imdbId: e.imdbId, name: e.name, year: releaseYearDisplay(e.year), releaseYear: e.year, added: e.added, isRated: e.isRated };
}

export function flatrateToTile(e: FlatrateEntry): TileEntry {
  return { imdbId: e.imdbId, name: e.name, year: releaseYearDisplay(e.year), releaseYear: e.year, added: e.added, isRated: e.isRated };
}

/**
 * `PaidEntry.year` is already a server-formatted display string ("Not yet released" included), so
 * there is no number to hand on — hence `releaseYear: null` rather than a year parsed back out of
 * the text. Costs nothing here: the eBay link is a dashboard feature and this row feeds a provider
 * page.
 */
export function paidToTile(e: PaidEntry): TileEntry {
  return { imdbId: e.imdbId, name: e.name, year: e.year, releaseYear: null, added: e.added, isRated: e.isRated };
}
