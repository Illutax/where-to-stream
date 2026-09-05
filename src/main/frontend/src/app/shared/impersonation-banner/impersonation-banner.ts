import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { TranslocoPipe } from '@jsverse/transloco';
import { ImpersonationApi } from '../../core/api/impersonation-api';
import { AuthStore } from '../../core/auth-store';

/**
 * The permanent, unmissable notice that an impersonation is running (ADR-0020).
 *
 * Renders nothing at all unless `impersonatedBy` is set, which is only ever the case for the admin
 * who started the switch. An ordinary user never receives that field and therefore never learns the
 * feature exists.
 *
 * It names both identities and carries the way out next to them, because the dangerous mistake is
 * not starting an impersonation — it is forgetting one is running and mistaking the other account's
 * session for one's own.
 */
@Component({
  selector: 'app-impersonation-banner',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [MatButtonModule, TranslocoPipe],
  template: `
    @if (authStore.impersonatedBy(); as admin) {
      <div class="impersonation-banner" role="status">
        <span>{{ 'impersonation.banner' | transloco: { user: authStore.username(), admin } }}</span>
        <button matButton="outlined" type="button" (click)="exit()">
          {{ 'impersonation.exit' | transloco }}
        </button>
      </div>
    }
  `,
  styles: `
    .impersonation-banner {
      display: flex;
      align-items: center;
      justify-content: center;
      gap: 1rem;
      flex-wrap: wrap;
      padding: 0.6rem 1rem;
      background: #8a4b00;
      color: #fff;
      font-weight: 600;
    }
  `,
})
export class ImpersonationBanner {
  protected readonly authStore = inject(AuthStore);
  private readonly api = inject(ImpersonationApi);

  protected exit(): void {
    // A full reload rather than a store refresh: the switch changes the identity behind every
    // cached page, list and preference in the running app, and reasoning about which of them to
    // invalidate is more error-prone than starting over.
    this.api.exit().subscribe({
      next: () => window.location.reload(),
      error: () => window.location.reload(),
    });
  }
}
