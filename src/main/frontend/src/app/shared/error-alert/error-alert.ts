import { ChangeDetectionStrategy, Component, input } from '@angular/core';

@Component({
  selector: 'app-error-alert',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @if (message()) {
      <div class="alert alert-danger" role="alert">{{ message() }}</div>
    }
  `,
})
export class ErrorAlert {
  readonly message = input<string | null>(null);
}
