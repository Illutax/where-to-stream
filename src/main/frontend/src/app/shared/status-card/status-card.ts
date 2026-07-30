import { ChangeDetectionStrategy, Component, input } from '@angular/core';
import { MatCardModule } from '@angular/material/card';
import { TranslocoPipe } from '@jsverse/transloco';
import { Status } from '../../core/models';

/** Presentational status card (version + server start time). */
@Component({
  selector: 'app-status-card',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [MatCardModule, TranslocoPipe],
  template: `
    <mat-card>
      <mat-card-header>
        <mat-card-title>{{ 'status.generalInfo' | transloco }}</mat-card-title>
      </mat-card-header>
      <mat-card-content>
        @if (loading()) {
          <p><span class="skeleton-bar skeleton-bar--narrow"></span></p>
          <p><span class="skeleton-bar"></span></p>
        } @else if (status(); as s) {
          <p>{{ 'status.version' | transloco }} <span>{{ s.version ?? ('status.dev' | transloco) }}</span></p>
          <p>{{ 'status.serverStart' | transloco }} <span>{{ s.serverStart }}</span></p>
        }
      </mat-card-content>
    </mat-card>
  `,
})
export class StatusCard {
  readonly status = input<Status | null>(null);
  /** While true, renders placeholder lines instead of {@link status} (still loading). */
  readonly loading = input(false);
}
