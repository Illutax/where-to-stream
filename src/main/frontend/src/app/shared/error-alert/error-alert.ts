import { ChangeDetectionStrategy, Component, input } from '@angular/core';

/**
 * Persistent inline error callout, styled with Material error tokens. Errors stay visible
 * (unlike the transient success snackbar), so this is a plain token-styled element rather than
 * a snackbar. The stable `error-alert` class / `role="alert"` are relied on by tests.
 */
@Component({
  selector: 'app-error-alert',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @if (message()) {
      <div class="error-alert" role="alert">{{ message() }}</div>
    }
  `,
})
export class ErrorAlert {
  readonly message = input<string | null>(null);
}
