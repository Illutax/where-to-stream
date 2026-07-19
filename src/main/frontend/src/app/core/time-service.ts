import { Injectable } from '@angular/core';

/**
 * Facade over the browser wall clock. All client-side time reads must go through this service
 * (never `Date.now()` / `new Date()` directly) so tests can substitute a fixed clock — via a
 * `{ provide: TimeService, useValue: … }` override — and assert against exact, repeatable
 * values.
 *
 * The app currently has no direct time read (server timestamps are rendered as-is); this is the
 * sanctioned entry point for any future one.
 */
@Injectable({ providedIn: 'root' })
export class TimeService {
  /** Current epoch milliseconds (replaces `Date.now()`). */
  now(): number {
    return Date.now();
  }

  /** Current date/time (replaces `new Date()`). */
  nowDate(): Date {
    return new Date();
  }
}
