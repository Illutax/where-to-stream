import { ConnectedPosition, OverlayModule } from '@angular/cdk/overlay';
import { ChangeDetectionStrategy, Component, input, signal } from '@angular/core';
import { ImdbId, posterFullUrl, posterUrl } from '../../core/domain';

/**
 * A small lazy-loaded poster thumbnail for a title. Missing posters (404 → the endpoint has none,
 * or the feature is off) hide the image gracefully. On hover it shows the high-resolution poster in
 * a CDK overlay (rendered at the document root, so it is not clipped by the tables' horizontal
 * scroll); the hi-res image is only requested while the overlay is open (fetched on demand).
 */
@Component({
  selector: 'app-poster-thumb',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [OverlayModule],
  template: `
    @if (!hidden()) {
      <img
        class="poster-thumb"
        cdkOverlayOrigin
        #origin="cdkOverlayOrigin"
        [src]="posterUrl(imdbId())"
        [alt]="name()"
        loading="lazy"
        (error)="hidden.set(true)"
        (mouseenter)="open.set(true)"
        (mouseleave)="open.set(false)" />

      <ng-template
        cdkConnectedOverlay
        [cdkConnectedOverlayOrigin]="origin"
        [cdkConnectedOverlayOpen]="open()"
        [cdkConnectedOverlayPositions]="positions">
        <img class="poster-hover" [src]="posterFullUrl(imdbId())" [alt]="name()" (error)="open.set(false)" />
      </ng-template>
    }
  `,
})
export class PosterThumb {
  readonly imdbId = input.required<ImdbId>();
  readonly name = input<string>('');

  protected readonly hidden = signal(false);
  protected readonly open = signal(false);
  protected readonly posterUrl = posterUrl;
  protected readonly posterFullUrl = posterFullUrl;

  // Prefer the hi-res preview to the right of the thumbnail, falling back to the left; and prefer
  // aligning to the thumbnail's top, falling back to its bottom so a poster hovered near the
  // bottom of the viewport flips upward instead of running off the bottom edge.
  protected readonly positions: ConnectedPosition[] = [
    { originX: 'end', originY: 'top', overlayX: 'start', overlayY: 'top', offsetX: 8 },
    { originX: 'start', originY: 'top', overlayX: 'end', overlayY: 'top', offsetX: -8 },
    { originX: 'end', originY: 'bottom', overlayX: 'start', overlayY: 'bottom', offsetX: 8 },
    { originX: 'start', originY: 'bottom', overlayX: 'end', overlayY: 'bottom', offsetX: -8 },
  ];
}
