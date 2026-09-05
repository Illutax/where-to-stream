import { computed, inject, Injectable, Signal, signal } from '@angular/core';
import { OffersApi } from './api/offers-api';
import { ImdbId } from './domain';
import { TitleOffers } from './models';

/** What the widget knows about one title's prices right now. */
export type OfferState =
  /** Nothing asked for yet — the resting state, and the one every title starts in. */
  | { readonly kind: 'idle' }
  | { readonly kind: 'loading' }
  /** The server answered. Whether it found anything is in `offers.status`. */
  | { readonly kind: 'loaded'; readonly offers: TitleOffers }
  /** The request itself failed (network, 5xx). Distinct from the server saying "unavailable". */
  | { readonly kind: 'error' };

const IDLE: OfferState = { kind: 'idle' };

/**
 * Per-title price lookup state.
 *
 * <p><strong>Nothing is fetched on its own.</strong> No prefetch on page load, no "load all"
 * anywhere: every lookup costs two calls from a daily budget the whole installation shares
 * (ADR-0017), so a lookup only ever happens because someone clicked. A dashboard with 200 titles
 * that fetched on render would spend 8% of the day's budget on one page view.
 *
 * <p>Requests for a title already in flight are dropped rather than queued. That is not the same as
 * the server's in-flight deduplication — this one only covers a single browser tab; the server's
 * covers two users, two tabs, and a script with a session cookie.
 *
 * <p>Results are kept for the lifetime of the page but are not a cache in any meaningful sense:
 * `refresh()` exists precisely because a bid can change within the minute, and an "as of" timestamp
 * is shown alongside every price for the same reason.
 */
@Injectable({ providedIn: 'root' })
export class OffersStore {
  private readonly api = inject(OffersApi);
  private readonly _states = signal<ReadonlyMap<ImdbId, OfferState>>(new Map());

  /** Counter, not a clock: it only has to differ between two refreshes of the same title. */
  private refreshCount = 0;

  /** The state of one title, defaulting to idle for a title never asked about. */
  stateFor(imdbId: Signal<ImdbId>): Signal<OfferState> {
    return computed(() => this._states().get(imdbId()) ?? IDLE);
  }

  /** Reads one title's state once, without creating a reactive dependency chain. */
  snapshot(imdbId: ImdbId): OfferState {
    return this._states().get(imdbId) ?? IDLE;
  }

  /**
   * Looks up prices for a title, unless a lookup is already running for it.
   *
   * <p>Does nothing when the title is already loaded — a second click on a price that is already
   * shown should not silently spend two more calls. Use {@link refresh} for a deliberate re-check.
   */
  load(imdbId: ImdbId): void {
    const current = this.snapshot(imdbId);
    if (current.kind === 'loading' || current.kind === 'loaded') {
      return;
    }
    this.fetch(imdbId, undefined);
  }

  /**
   * Re-checks a title the user is already looking at, bypassing the browser cache.
   *
   * <p>Without the cache-buster the browser would answer from its own 90-second cache and the
   * button would appear broken.
   */
  refresh(imdbId: ImdbId): void {
    if (this.snapshot(imdbId).kind === 'loading') {
      return;
    }
    this.fetch(imdbId, ++this.refreshCount);
  }

  private fetch(imdbId: ImdbId, cacheBuster: number | undefined): void {
    this.setState(imdbId, { kind: 'loading' });
    this.api.get(imdbId, cacheBuster).subscribe({
      next: (offers) => this.setState(imdbId, { kind: 'loaded', offers }),
      error: () => this.setState(imdbId, { kind: 'error' }),
    });
  }

  private setState(imdbId: ImdbId, state: OfferState): void {
    this._states.update((states) => new Map(states).set(imdbId, state));
  }
}
