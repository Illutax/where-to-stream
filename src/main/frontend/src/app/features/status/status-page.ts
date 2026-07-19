import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { StatusApi } from '../../core/api/status-api';
import { Status } from '../../core/models';
import { ErrorAlert } from '../../shared/error-alert/error-alert';
import { Loading } from '../../shared/loading/loading';
import { StatusCard } from '../../shared/status-card/status-card';

/** Container: loads build/runtime status. */
@Component({
  selector: 'app-status-page',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [StatusCard, Loading, ErrorAlert],
  template: `
    <h1 class="h3 mb-3">Status</h1>
    @if (loading()) {
      <app-loading />
    } @else if (error()) {
      <app-error-alert [message]="error()" />
    } @else if (status(); as s) {
      <app-status-card [status]="s" />
    }
  `,
})
export class StatusPage {
  private readonly api = inject(StatusApi);

  protected readonly status = signal<Status | null>(null);
  protected readonly loading = signal(true);
  protected readonly error = signal<string | null>(null);

  constructor() {
    this.api.getStatus().subscribe({
      next: (status) => {
        this.status.set(status);
        this.loading.set(false);
      },
      error: () => {
        this.error.set('Failed to load status.');
        this.loading.set(false);
      },
    });
  }
}
