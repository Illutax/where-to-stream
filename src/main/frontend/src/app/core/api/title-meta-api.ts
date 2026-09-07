import { HttpClient } from '@angular/common/http';
import { inject, Injectable, signal, Signal, WritableSignal } from '@angular/core';
import { Observable } from 'rxjs';
import { API_BASE } from '../api-base';
import { ImdbId } from '../domain';
import { TitleMetaResponse } from '../models';

/** Per-title metadata (age rating + German title), cached server-side per title. */
@Injectable({ providedIn: 'root' })
export class TitleMetaApi {
  private readonly http = inject(HttpClient);
  private readonly base = inject(API_BASE);

  /** One signal per title, shared by every component that shows that title. */
  private readonly cache = new Map<ImdbId, WritableSignal<TitleMetaResponse | null>>();
  /** Titles with a request in flight — without this, n consumers would fire n requests. */
  private readonly inFlight = new Set<ImdbId>();

  get(imdbId: ImdbId): Observable<TitleMetaResponse> {
    return this.http.get<TitleMetaResponse>(`${this.base}titles/${imdbId}/meta`);
  }

  /**
   * The shared signal for one title — the same instance for every caller, empty until loaded.
   *
   * <p>Shared rather than per component because a single row now has two consumers: the title cell
   * (age badge, German title) and the eBay column (German title in the search term). Handing each
   * its own signal would double the requests a dashboard makes, and the count is already one per
   * row (TODO-59).
   */
  metaOf(imdbId: ImdbId): Signal<TitleMetaResponse | null> {
    return this.slotFor(imdbId).asReadonly();
  }

  /**
   * Fetches a title's metadata unless it is already loaded or already being fetched.
   *
   * <p>Callers may invoke this freely, once per row and per consumer; while a request is in flight
   * or its answer is in, no further one goes out.
   *
   * <p>A failure is deliberately <em>not</em> remembered: the slot stays empty, so the next consumer
   * of that title tries again. That keeps a transient blip from hiding a badge for the rest of the
   * session, at the price of one request per consumer for a title that is genuinely broken — which
   * is what happened before this cache existed, so nothing got worse.
   */
  prefetch(imdbId: ImdbId): void {
    const slot = this.slotFor(imdbId);
    if (slot() !== null || this.inFlight.has(imdbId)) {
      return;
    }
    this.inFlight.add(imdbId);
    this.get(imdbId).subscribe({
      next: (meta) => {
        this.inFlight.delete(imdbId);
        slot.set(meta);
      },
      error: () => this.inFlight.delete(imdbId),
    });
  }

  private slotFor(imdbId: ImdbId): WritableSignal<TitleMetaResponse | null> {
    const existing = this.cache.get(imdbId);
    if (existing) {
      return existing;
    }
    const created = signal<TitleMetaResponse | null>(null);
    this.cache.set(imdbId, created);
    return created;
  }
}
