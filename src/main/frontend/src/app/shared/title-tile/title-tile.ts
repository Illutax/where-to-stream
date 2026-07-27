import { ChangeDetectionStrategy, Component, computed, inject, input, output, signal } from '@angular/core';
import { TranslocoPipe } from '@jsverse/transloco';
import { AgeRatingStore } from '../../core/age-rating-store';
import { ImdbId, imdbUrl, posterFullUrl, WatchlistDate } from '../../core/domain';
import { GermanTitleStore } from '../../core/german-title-store';
import { injectTitleMeta } from '../../core/title-meta';
import { AgeBadge } from '../age-badge/age-badge';
import { splitTitle, titleSizeSteps } from './title-split';

/**
 * A poster tile for the grid view: the poster art with a bottom scrim, the (auto-shrinking, never
 * truncated) title, a year/age-rating badge stack, an added-date chip revealed on hover/focus, and
 * a watched toggle. The chrome sitting directly on top of the poster artwork (scrim, hairline,
 * title, chips) uses fixed dark-scrim/light-ink colors for legibility regardless of the app's
 * light/dark theme — like a photo app's caption overlay; only the watched-toggle's *watched* state
 * (an opaque chip, not translucent-over-art) is mapped to the Material theme.
 */
@Component({
  selector: 'app-title-tile',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [AgeBadge, TranslocoPipe],
  template: `
    <div class="title-tile" [class.recently-changed]="recentlyChanged()">
      <div class="poster-box" [class.watched]="isRated()">
        @if (!hidden()) {
          <img
            class="poster-img"
            [src]="posterFullUrl(imdbId())"
            [alt]="name()"
            loading="lazy"
            (error)="hidden.set(true)" />
        }
        <div class="scrim"></div>
        <div class="hairline"></div>
        <div class="title-block">
          <div
            class="main-title"
            [style.font-size.px]="sizeSteps().mainSize"
            [style.line-height]="sizeSteps().mainLineHeight">
            <a [href]="imdbUrl(imdbId())" target="_blank" rel="noopener">{{ titleParts().main }}</a>
          </div>
          @if (titleParts().sub) {
            <div class="subtitle" [style.font-size.px]="sizeSteps().subSize">{{ titleParts().sub }}</div>
          }
        </div>
        <div class="badge-stack">
          <span class="year-chip">{{ year() }}</span>
          @if (ageRatingStore.showAgeRatings() && meta()?.rating; as rating) {
            <app-age-badge [rating]="rating" />
          }
        </div>
        <div class="date-chip">+ {{ added() }}</div>
      </div>
      <button
        type="button"
        class="watched-toggle"
        [class.watched]="isRated()"
        (click)="seenToggle.emit({ imdbId: imdbId(), seen: !isRated() })"
        [attr.aria-pressed]="isRated()"
        [attr.aria-label]="(isRated() ? 'table.markNotSeen' : 'table.markSeen') | transloco: { name: name() }">
        <span aria-hidden="true">✓</span>
      </button>
    </div>
  `,
  styles: `
    .title-tile {
      position: relative;
      transition: transform 0.18s ease;
    }
    @media (prefers-reduced-motion: no-preference) {
      .title-tile:hover,
      .title-tile:focus-within {
        transform: translateY(-3px);
      }
    }
    .title-tile.recently-changed .poster-box {
      animation: tile-flash 4s ease-out;
    }
    @keyframes tile-flash {
      from {
        box-shadow: 0 0 0 3px var(--mat-sys-tertiary-container);
      }
      to {
        box-shadow: 0 0 0 0 transparent;
      }
    }

    .poster-box {
      position: relative;
      width: 100%;
      aspect-ratio: 2 / 3;
      border-radius: 3px;
      overflow: hidden;
      background: var(--mat-sys-surface-variant);
      transition: filter 0.25s ease, opacity 0.25s ease;
    }
    .poster-box.watched {
      filter: grayscale(1) contrast(0.92);
      opacity: 0.46;
    }

    .poster-img {
      position: absolute;
      inset: 0;
      width: 100%;
      height: 100%;
      object-fit: cover;
    }

    .scrim {
      position: absolute;
      left: 0;
      right: 0;
      bottom: 0;
      height: 62%;
      pointer-events: none;
      background: linear-gradient(
        to top,
        rgba(16, 14, 12, 0.94) 0%,
        rgba(16, 14, 12, 0.86) 18%,
        rgba(16, 14, 12, 0.58) 46%,
        rgba(16, 14, 12, 0) 100%
      );
    }

    .hairline {
      position: absolute;
      inset: 0;
      pointer-events: none;
      box-shadow: inset 0 0 0 1px rgba(243, 236, 225, 0.14);
    }

    .title-block {
      position: absolute;
      left: 12px;
      right: 12px;
      bottom: 34px;
      pointer-events: none;
      display: flex;
      flex-direction: column;
      gap: 3px;
    }
    .main-title {
      font-weight: 500;
      color: #f3ece1;
      text-wrap: pretty;
      text-shadow: 0 1px 8px rgba(16, 14, 12, 0.6);
    }
    .main-title a {
      pointer-events: auto;
      color: inherit;
      text-decoration: none;
    }
    .main-title a:hover,
    .main-title a:focus-visible {
      text-decoration: underline;
    }
    .subtitle {
      font-weight: 400;
      line-height: 1.3;
      color: rgba(243, 236, 225, 0.6);
      text-wrap: pretty;
    }

    .badge-stack {
      position: absolute;
      top: 8px;
      right: 8px;
      pointer-events: none;
      display: flex;
      flex-direction: column;
      align-items: flex-end;
      gap: 5px;
    }
    .year-chip {
      padding: 4px 6px;
      background: rgba(16, 14, 12, 0.72);
      backdrop-filter: blur(4px);
      border-radius: 3px;
      font: 500 10px/1 inherit;
      color: #f3ece1;
    }

    .date-chip {
      position: absolute;
      right: 8px;
      bottom: 8px;
      padding: 4px 7px;
      background: rgba(16, 14, 12, 0.82);
      backdrop-filter: blur(4px);
      border-radius: 3px;
      font-size: 9.5px;
      letter-spacing: 0.03em;
      color: rgba(243, 236, 225, 0.82);
      pointer-events: none;
      opacity: 0;
      transform: translateY(4px);
    }
    @media (prefers-reduced-motion: no-preference) {
      .date-chip {
        transition: opacity 0.18s ease, transform 0.18s ease;
      }
    }
    .title-tile:hover .date-chip,
    .title-tile:focus-within .date-chip {
      opacity: 1;
      transform: translateY(0);
    }

    .watched-toggle {
      position: absolute;
      left: 8px;
      bottom: 8px;
      width: 24px;
      height: 24px;
      padding: 0;
      border: none;
      background: none;
      border-radius: 50%;
      cursor: pointer;
      display: flex;
      align-items: center;
      justify-content: center;
    }
    .watched-toggle::before {
      content: '';
      position: absolute;
      inset: 0;
      border-radius: 50%;
      background: rgba(16, 14, 12, 0.55);
      border: 1.5px solid rgba(243, 236, 225, 0.45);
      transition: background 0.2s ease, border-color 0.2s ease;
    }
    .watched-toggle.watched::before {
      background: var(--mat-sys-primary);
      border-color: var(--mat-sys-primary);
    }
    .watched-toggle span {
      position: relative;
      font: 600 12px/1 inherit;
      color: rgba(243, 236, 225, 0.55);
      transition: color 0.2s ease;
    }
    .watched-toggle.watched span {
      color: var(--mat-sys-on-primary);
    }
    @media (pointer: coarse) {
      .watched-toggle {
        width: 44px;
        height: 44px;
        left: -2px;
        bottom: -2px;
      }
      .watched-toggle::before {
        inset: 10px;
      }
    }
  `,
})
export class TitleTile {
  readonly imdbId = input.required<ImdbId>();
  readonly name = input<string>('');
  readonly year = input.required<string>();
  readonly added = input.required<WatchlistDate>();
  readonly isRated = input.required<boolean>();
  readonly recentlyChanged = input(false);
  readonly seenToggle = output<{ imdbId: ImdbId; seen: boolean }>();

  protected readonly ageRatingStore = inject(AgeRatingStore);
  private readonly germanTitleStore = inject(GermanTitleStore);
  protected readonly imdbUrl = imdbUrl;
  protected readonly posterFullUrl = posterFullUrl;
  protected readonly hidden = signal(false);
  protected readonly meta = injectTitleMeta(() => this.imdbId());

  /** The German title when the preference is on and one exists, else the original (English) name. */
  private readonly displayTitle = computed(
    () => (this.germanTitleStore.show() && this.meta()?.germanTitle) || this.name(),
  );
  protected readonly titleParts = computed(() => splitTitle(this.displayTitle()));
  protected readonly sizeSteps = computed(() => titleSizeSteps(this.titleParts().main.length));
}
