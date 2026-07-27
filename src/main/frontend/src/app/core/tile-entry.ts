import { ImdbId, releaseYearDisplay, WatchlistDate } from './domain';
import { FlatrateEntry, OverviewEntry, PaidEntry } from './models';

/**
 * The common row shape the poster-tile grid renders — normalized from the three differing table
 * row DTOs (which carry columns the grid doesn't show: services / price / languages).
 */
export interface TileEntry {
  imdbId: ImdbId;
  name: string;
  year: string;
  added: WatchlistDate;
  isRated: boolean;
}

export function overviewToTile(e: OverviewEntry): TileEntry {
  return { imdbId: e.imdbId, name: e.name, year: releaseYearDisplay(e.year), added: e.added, isRated: e.isRated };
}

export function flatrateToTile(e: FlatrateEntry): TileEntry {
  return { imdbId: e.imdbId, name: e.name, year: releaseYearDisplay(e.year), added: e.added, isRated: e.isRated };
}

/** `PaidEntry.year` is already a server-formatted display string ("Not yet released" included). */
export function paidToTile(e: PaidEntry): TileEntry {
  return { imdbId: e.imdbId, name: e.name, year: e.year, added: e.added, isRated: e.isRated };
}
