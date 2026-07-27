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
        <p>{{ 'status.version' | transloco }} <span>{{ status().version ?? ('status.dev' | transloco) }}</span></p>
        <p>{{ 'status.serverStart' | transloco }} <span>{{ status().serverStart }}</span></p>
      </mat-card-content>
    </mat-card>
  `,
})
export class StatusCard {
  readonly status = input.required<Status>();
}
