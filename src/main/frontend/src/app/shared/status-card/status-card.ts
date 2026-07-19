import { ChangeDetectionStrategy, Component, input } from '@angular/core';
import { MatCardModule } from '@angular/material/card';
import { Status } from '../../core/models';

/** Presentational status card (version + server start time). */
@Component({
  selector: 'app-status-card',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [MatCardModule],
  template: `
    <mat-card>
      <mat-card-header>
        <mat-card-title>General Information</mat-card-title>
      </mat-card-header>
      <mat-card-content>
        <p>Version: <span>{{ status().version ?? 'dev' }}</span></p>
        <p>Server start: <span>{{ status().serverStart }}</span></p>
      </mat-card-content>
    </mat-card>
  `,
})
export class StatusCard {
  readonly status = input.required<Status>();
}
