import { ChangeDetectionStrategy, Component, input } from '@angular/core';
import { Status } from '../../core/models';

/** Presentational status card (version + server start time). */
@Component({
  selector: 'app-status-card',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="card">
      <div class="card-header">General Information</div>
      <div class="card-body">
        <p class="mb-1">Version: <span>{{ status().version ?? 'dev' }}</span></p>
        <p class="mb-0">Server start: <span>{{ status().serverStart }}</span></p>
      </div>
    </div>
  `,
})
export class StatusCard {
  readonly status = input.required<Status>();
}
