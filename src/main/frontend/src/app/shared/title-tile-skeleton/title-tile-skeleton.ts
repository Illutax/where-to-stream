import { ChangeDetectionStrategy, Component } from '@angular/core';

/**
 * Placeholder for {@link TitleTile} while the catalogue is loading: the same 2:3 poster box,
 * gently pulsing, so the grid doesn't reflow once real tiles arrive.
 */
@Component({
  selector: 'app-title-tile-skeleton',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `<div class="title-tile-skeleton"></div>`,
  styles: `
    .title-tile-skeleton {
      width: 100%;
      aspect-ratio: 2 / 3;
      border-radius: 3px;
      background: var(--mat-sys-surface-variant);
    }
    @media (prefers-reduced-motion: no-preference) {
      .title-tile-skeleton {
        animation: skeleton-pulse 1.1s ease-in-out infinite;
      }
    }
    @keyframes skeleton-pulse {
      0%,
      100% {
        opacity: 1;
      }
      50% {
        opacity: 0.4;
      }
    }
  `,
})
export class TitleTileSkeleton {}
