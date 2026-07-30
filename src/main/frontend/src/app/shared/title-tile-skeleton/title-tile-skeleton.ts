import { ChangeDetectionStrategy, Component } from '@angular/core';

/**
 * Placeholder for {@link TitleTile} while the catalogue is loading: the same 2:3 poster box,
 * with a left-to-right shimmer sweep, so the grid doesn't reflow once real tiles arrive.
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
      background-image: linear-gradient(
        90deg,
        var(--mat-sys-surface-variant) 8%,
        color-mix(in srgb, var(--mat-sys-surface-variant), var(--mat-sys-on-surface-variant) 22%) 18%,
        var(--mat-sys-surface-variant) 33%
      );
      background-size: 200% 100%;
      background-position: -150% 0;
    }
    @media (prefers-reduced-motion: no-preference) {
      .title-tile-skeleton {
        animation: skeleton-shimmer 1.5s ease-in-out infinite;
      }
    }
    @keyframes skeleton-shimmer {
      100% {
        background-position: 150% 0;
      }
    }
  `,
})
export class TitleTileSkeleton {}
