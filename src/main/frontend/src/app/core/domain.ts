/**
 * Client-side domain value types mirroring the backend value objects (ImdbId, ReleaseYear,
 * WatchlistDate). They are TypeScript *branded* types: a branded value is still a plain string /
 * number at runtime (so the JSON parsed by HttpClient needs no conversion), but the brand stops
 * a raw string or number being used where a domain value is expected. The smart constructors are
 * the one place a raw value becomes a domain value, and this module also owns the small pieces of
 * domain logic (the canonical IMDb URL, the "Not yet released" rendering).
 */

export type ImdbId = string & { readonly __brand: 'ImdbId' };
export type ReleaseYear = number & { readonly __brand: 'ReleaseYear' };
export type WatchlistDate = string & { readonly __brand: 'WatchlistDate' };

const IMDB_ID = /^tt\w+$/;

/** Wrap a raw string as an ImdbId, validating the {@code tt…} format. */
export function imdbId(value: string): ImdbId {
  if (!IMDB_ID.test(value)) {
    throw new Error(`Invalid IMDb id: ${value}`);
  }
  return value as ImdbId;
}

/** The canonical IMDb URL for a title. */
export function imdbUrl(id: ImdbId): string {
  return `https://www.imdb.com/title/${id}`;
}

export function releaseYear(value: number): ReleaseYear {
  return value as ReleaseYear;
}

export const NOT_YET_RELEASED = 'Not yet released';

/** Human-readable year, or the "Not yet released" placeholder for a title with no year (0). */
export function releaseYearDisplay(year: ReleaseYear): string {
  return year > 0 ? String(year) : NOT_YET_RELEASED;
}

export function watchlistDate(value: string): WatchlistDate {
  return value as WatchlistDate;
}
