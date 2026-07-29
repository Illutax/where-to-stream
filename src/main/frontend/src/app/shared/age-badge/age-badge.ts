import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';
import { AgeRating } from '../../core/models';

/**
 * A small FSK-coloured age-rating badge.
 * Purely presentational — the rating is passed in.
 * FSK ratings (0/6/12/16/18) get the official colour scheme;
 * a foreign fallback certificate is shown neutral grey.
 */
@Component({
  selector: 'app-age-badge',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `<span class="age-badge" [class]="badgeClass()" [title]="tooltip()">{{ rating().label }}</span>`,
})
export class AgeBadge {
  readonly rating = input.required<AgeRating>();

  protected readonly badgeClass = computed(() =>
    this.rating().system === 'FSK' ? `age-badge--fsk-${this.rating().label}` : 'age-badge--other',
  );
  protected readonly tooltip = computed(() =>
    this.rating().system === 'FSK' ? `FSK ${this.rating().label}` : `Age rating: ${this.rating().label}`,
  );
}
