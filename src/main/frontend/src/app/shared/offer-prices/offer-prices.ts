import { ChangeDetectionStrategy, Component, computed, inject, input } from '@angular/core';
import { MatTooltipModule } from '@angular/material/tooltip';
import { TranslocoPipe, TranslocoService } from '@jsverse/transloco';
import { ImdbId } from '../../core/domain';
import { Offer } from '../../core/models';
import { OffersStore } from '../../core/offers-store';
import { UserPrefsStore } from '../../core/user-prefs-store';

/** What a price costs all in — shipping counts only when the listing states it. */
export function offerTotalCents(offer: Offer): number {
  return offer.amountCents + (offer.shippingCents ?? 0);
}

/** The cheaper of the two offers by total, or whichever one exists. */
export function cheaperOffer(a: Offer | null, b: Offer | null): Offer | null {
  if (!a) return b;
  if (!b) return a;
  return offerTotalCents(a) <= offerTotalCents(b) ? a : b;
}

/** Minor units to a localised amount — `1299`/`EUR` becomes `12,99 €` in German. */
export function formatPrice(amountCents: number, currency: string, locale: string): string {
  return new Intl.NumberFormat(locale, { style: 'currency', currency }).format(amountCents / 100);
}

/**
 * The eBay price widget for one title: a button at rest, one price inline once loaded, and both
 * prices with an "as of" stamp in a tooltip (plan, decision 6.6).
 *
 * <p>Nothing is fetched until the button is pressed. That is a hard rule of the feature rather than
 * a nicety — every lookup spends two calls from a budget the whole installation shares (ADR-0017).
 *
 * <p>The inline value reads "from …" on purpose. On a touch device there is no hover, so a user who
 * never opens the tooltip sees only that one number; "from" is what tells them a second price
 * exists. For the same reason the tooltip is reachable in three ways — hover, keyboard focus, and
 * an explicit button for tapping — where a plain `title` attribute would have covered only the
 * first.
 *
 * <p>The offer link is a plain anchor with `rel="noopener"`, and the tooltip is a plain string.
 * Nothing here interpolates markup: the amounts and the URL originate in an eBay response, and the
 * server already strips everything else out (plan, section 5.3).
 */
@Component({
  selector: 'app-offer-prices',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [MatTooltipModule, TranslocoPipe],
  template: `
    @let state = offerState();
    @switch (state.kind) {
      @case ('idle') {
        <button type="button" class="offer-load" (click)="load()"
                [attr.aria-label]="'offers.loadFor' | transloco: { name: name() }">
          {{ 'offers.load' | transloco }}
        </button>
      }
      @case ('loading') {
        <span class="skeleton-bar skeleton-bar--narrow"
              [attr.aria-label]="'offers.loading' | transloco" role="status"></span>
      }
      @case ('error') {
        <span class="offer-note text-muted">{{ 'offers.unavailable' | transloco }}</span>
      }
      @case ('loaded') {
        @if (cheapest(); as offer) {
          <span class="offer-prices">
            <a #tt="matTooltip" class="offer-price" [href]="offer.url" target="_blank" rel="noopener"
               [matTooltip]="details()" matTooltipTouchGestures="on">
              {{ 'offers.from' | transloco: { price: formatted(offer) } }}
            </a>
            <button type="button" class="offer-details" (click)="tt.toggle()"
                    [attr.aria-label]="'offers.details' | transloco">ⓘ</button>
            <button type="button" class="offer-refresh" (click)="refresh()"
                    [attr.aria-label]="'offers.refresh' | transloco">↻</button>
          </span>
        } @else {
          <span class="offer-note text-muted">{{ noOfferMessageKey() | transloco }}</span>
        }
      }
    }
  `,
})
export class OfferPrices {
  readonly imdbId = input.required<ImdbId>();
  /** Only for the accessible label on the resting button, so it names the title it belongs to. */
  readonly name = input<string>('');

  private readonly store = inject(OffersStore);
  private readonly transloco = inject(TranslocoService);
  private readonly userPrefs = inject(UserPrefsStore);

  protected readonly offerState = this.store.stateFor(this.imdbId);

  private readonly locale = computed(() => (this.userPrefs.language() === 'DE' ? 'de-DE' : 'en-GB'));

  /** The offer shown inline: the cheaper of fixed price and auction, by total. */
  protected readonly cheapest = computed(() => {
    const state = this.offerState();
    if (state.kind !== 'loaded' || state.offers.status !== 'FETCHED') {
      return null;
    }
    return cheaperOffer(state.offers.buyNow, state.offers.auction);
  });

  /**
   * Why there is no price. The three reasons are kept apart because they call for different things
   * from the reader: try later, wait until tomorrow, or accept that nobody is selling it.
   */
  protected readonly noOfferMessageKey = computed(() => {
    const state = this.offerState();
    if (state.kind !== 'loaded') {
      return 'offers.unavailable';
    }
    switch (state.offers.status) {
      case 'USER_ALLOWANCE_REACHED':
        return 'offers.userLimit';
      case 'GLOBAL_BUDGET_EXHAUSTED':
        return 'offers.globalLimit';
      case 'UNAVAILABLE':
        return 'offers.unavailable';
      default:
        return 'offers.none';
    }
  });

  /** The tooltip: both prices and the timestamp, as one plain string (never markup). */
  protected readonly details = computed(() => {
    const state = this.offerState();
    if (state.kind !== 'loaded') {
      return '';
    }
    const lines: string[] = [];
    if (state.offers.buyNow) {
      lines.push(this.line('offers.buyNow', state.offers.buyNow));
    }
    if (state.offers.auction) {
      lines.push(this.line('offers.auction', state.offers.auction));
    }
    if (state.offers.fetchedAt) {
      lines.push(
        this.transloco.translate('offers.asOf', {
          time: new Date(state.offers.fetchedAt).toLocaleTimeString(this.locale()),
        }),
      );
    }
    return lines.join('\n');
  });

  protected formatted(offer: Offer): string {
    return formatPrice(offer.amountCents, offer.currency, this.locale());
  }

  protected load(): void {
    this.store.load(this.imdbId());
  }

  protected refresh(): void {
    this.store.refresh(this.imdbId());
  }

  /**
   * One tooltip line. Unstated shipping is spelled out rather than shown as zero — the client must
   * not turn "we do not know" into "it is free".
   */
  private line(key: string, offer: Offer): string {
    const price = this.formatted(offer);
    const shipping =
      offer.shippingCents === null
        ? this.transloco.translate('offers.shippingUnknown')
        : this.transloco.translate('offers.shipping', {
            price: formatPrice(offer.shippingCents, offer.currency, this.locale()),
          });
    return `${this.transloco.translate(key)}: ${price} (${shipping})`;
  }
}
