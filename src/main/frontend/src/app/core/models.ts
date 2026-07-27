/**
 * TypeScript mirrors of the server-side DTOs (tech.dobler.werstreamt.application.dto).
 * Kept 1:1 with the JSON shape returned by the /api endpoints — no client-side reshaping. The
 * imdbId/year/added fields carry the branded domain value types (see core/domain.ts); at runtime
 * they are the same string/number the JSON already holds.
 */

import { ImdbId, ReleaseYear, WatchlistDate } from './domain';

export interface OverviewEntry {
  isRated: boolean;
  name: string;
  imdbId: ImdbId;
  year: ReleaseYear;
  added: WatchlistDate;
  /** Comma-separated available services, or null when unavailable. */
  services: string | null;
}

export interface FlatrateEntry {
  isRated: boolean;
  name: string;
  imdbId: ImdbId;
  year: ReleaseYear;
  added: WatchlistDate;
}

export interface PaidEntry {
  name: string;
  imdbId: ImdbId;
  /** Pre-formatted German price string (formatted on the server). */
  price: string;
  added: WatchlistDate;
  isRated: boolean;
  /** Year as text; "Not yet released" for unreleased titles (formatted on the server). */
  year: string;
  languages: string | null;
}

export interface ProviderPage {
  provider: string;
  included: FlatrateEntry[];
  paid: PaidEntry[];
}

export interface ManageRow {
  imdbId: ImdbId;
  name: string;
  isRated: boolean;
  needsScrape: boolean;
}

export interface ManagePage {
  rows: ManageRow[];
  needsScrapeCount: number;
}

/** Status of the current user's watchlist (mirrors the server WatchlistDto). */
export interface WatchlistStatus {
  count: number;
  /** ISO timestamp of the most recent import, or null when the watchlist is empty. */
  lastImportedAt: string | null;
}

/** Outcome of a CSV import full-sync (mirrors the server WatchlistImportResultDto). */
export interface WatchlistImportResult {
  added: number;
  updated: number;
  removed: number;
  total: number;
}

export interface InvalidateResult {
  invalidated: number;
}

export interface ScrapeResult {
  scraped: number;
}

export interface Status {
  version: string | null;
  serverStart: string;
}

/** UI colour-scheme preference (mirrors the server Theme enum). */
export type Theme = 'SYSTEM' | 'LIGHT' | 'DARK';

/** The current principal (mirrors the server MeDto). */
export interface Me {
  authenticated: boolean;
  username: string | null;
  roles: string[];
  admin: boolean;
  theme: Theme;
  /** Whether TMDB is the active poster source (drives the TMDB attribution footer). */
  tmdbAttribution: boolean;
  /** Whether the user sees the FSK age-rating badges. */
  showAgeRatings: boolean;
}

/** Rating system of an age rating (mirrors the server AgeRating.RatingSystem). */
export type RatingSystem = 'FSK' | 'OTHER';

/** A title's age rating (mirrors the server AgeRating). */
export interface AgeRating {
  system: RatingSystem;
  label: string;
}

/** A user account in the administration UI (mirrors the server UserDto). */
export interface AdminUser {
  id: string;
  username: string;
  email: string | null;
  enabled: boolean;
  roles: string[];
  provider: string;
}

export interface CreateUserRequest {
  username: string;
  password: string;
  email: string | null;
  roles: string[];
}

export interface UpdateUserRequest {
  email: string | null;
  roles: string[];
  enabled: boolean;
}

/** Static provider metadata for the navbar and provider page (keys match StreamingProvider). */
export interface ProviderInfo {
  key: string;
  label: string;
}

export const PROVIDERS: ProviderInfo[] = [
  { key: 'disney', label: 'Disney+' },
  { key: 'amazon', label: 'Amazon Prime' },
  { key: 'youtube', label: 'Youtube Store' },
  { key: 'netflix', label: 'Netflix' },
  { key: 'wow', label: 'Sky WOW' },
];
