/**
 * TypeScript mirrors of the server-side DTOs (tech.dobler.werstreamt.application.dto).
 * Kept 1:1 with the JSON shape returned by the /api endpoints — no client-side reshaping.
 */

export interface OverviewEntry {
  isRated: boolean;
  name: string;
  imdbId: string;
  year: number;
  added: string;
  /** Comma-separated available services, or null when unavailable. */
  services: string | null;
}

export interface FlatrateEntry {
  isRated: boolean;
  name: string;
  imdbId: string;
  year: number;
  added: string;
}

export interface PaidEntry {
  name: string;
  imdbId: string;
  /** Pre-formatted German price string (formatted on the server). */
  price: string;
  added: string;
  isRated: boolean;
  /** Year as text; "Not yet released" for unreleased titles. */
  year: string;
  languages: string | null;
}

export interface ProviderPage {
  provider: string;
  included: FlatrateEntry[];
  paid: PaidEntry[];
}

export interface ManageRow {
  imdbId: string;
  name: string;
  isRated: boolean;
  needsScrape: boolean;
}

export interface ManagePage {
  rows: ManageRow[];
  needsScrapeCount: number;
}

export interface ListSelection {
  current: string;
  available: string[];
}

export interface ChangeListResult {
  selected: string;
  cached: number;
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

/** The current principal (mirrors the server MeDto). */
export interface Me {
  authenticated: boolean;
  username: string | null;
  roles: string[];
  admin: boolean;
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
  { key: 'google', label: 'Google Play' },
  { key: 'netflix', label: 'Netflix' },
  { key: 'wow', label: 'Sky WOW' },
];
