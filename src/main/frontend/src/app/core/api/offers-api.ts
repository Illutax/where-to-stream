import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { API_BASE } from '../api-base';
import { ImdbId } from '../domain';
import { TitleOffers } from '../models';

/**
 * Current eBay prices for one title on the caller's watchlist.
 *
 * <p>Takes an id and nothing else. There is deliberately no search parameter: the server builds the
 * eBay search term from data it already holds, which is what keeps this endpoint from being an
 * authenticated proxy to eBay's search.
 */
@Injectable({ providedIn: 'root' })
export class OffersApi {
  private readonly http = inject(HttpClient);
  private readonly base = inject(API_BASE);

  /**
   * @param cacheBuster a value that changes per explicit refresh. The response carries
   *   `Cache-Control: private, max-age=90`, which is what makes a re-render or a back-and-forth
   *   navigation free — but it would also make a deliberate "check again" click do nothing.
   *   A changing query parameter is the client's way around its own cache; the server offers no
   *   force flag, because that would be a way to spend the shared daily budget faster with no gate.
   */
  get(imdbId: ImdbId, cacheBuster?: number): Observable<TitleOffers> {
    const suffix = cacheBuster === undefined ? '' : `?refresh=${cacheBuster}`;
    return this.http.get<TitleOffers>(`${this.base}titles/${imdbId}/offers${suffix}`);
  }
}
