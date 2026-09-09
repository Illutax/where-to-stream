import { ImdbId, ReleaseYear, WatchlistDate } from './domain';
import { FlatrateEntry, OverviewEntry, PaidEntry } from './models';

/**
 * The common row shape the poster-tile grid renders — normalized from the three differing table
 * row DTOs (which carry columns the grid doesn't show: services / price / languages).
 *
 * <p>The year is the number, not display text. It used to be both: a formatted `year` plus a
 * nullable `releaseYear`, with nothing binding the two together — because `PaidEntry` only ever
 * arrived pre-formatted from the server. Now that it does not (TODO-60), one field does, and the
 * tile formats it where it is shown.
 */
export interface TileEntry {
  imdbId: ImdbId;
  name: string;
  year: ReleaseYear;
  added: WatchlistDate;
  isRated: boolean;
}

export function overviewToTile(e: OverviewEntry): TileEntry {
  return { imdbId: e.imdbId, name: e.name, year: e.year, added: e.added, isRated: e.isRated };
}

export function flatrateToTile(e: FlatrateEntry): TileEntry {
  return { imdbId: e.imdbId, name: e.name, year: e.year, added: e.added, isRated: e.isRated };
}

export function paidToTile(e: PaidEntry): TileEntry {
  return { imdbId: e.imdbId, name: e.name, year: e.year, added: e.added, isRated: e.isRated };
}
