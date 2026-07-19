import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { ActivatedRoute } from '@angular/router';
import { ProviderApi } from '../../core/api/provider-api';
import { PROVIDERS, ProviderPage as ProviderPageDto } from '../../core/models';
import { ErrorAlert } from '../../shared/error-alert/error-alert';
import { FlatrateTable } from '../../shared/flatrate-table/flatrate-table';
import { Loading } from '../../shared/loading/loading';
import { PaidTable } from '../../shared/paid-table/paid-table';

/**
 * Container for all five provider pages. Reacts to the {@code :key} route param (the same
 * component instance is reused when navigating between providers) and renders the flatrate
 * and/or paid tables depending on what the server returns.
 */
@Component({
  selector: 'app-provider-page',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [FlatrateTable, PaidTable, Loading, ErrorAlert],
  template: `
    <h1>{{ label() }}</h1>
    @if (loading()) {
      <app-loading />
    } @else if (error()) {
      <app-error-alert [message]="error()" />
    } @else if (page(); as p) {
      @if (p.included.length > 0) {
        <h2>Included</h2>
        <app-flatrate-table [entries]="p.included" />
      }
      @if (p.paid.length > 0) {
        <h2>Buy / Rent</h2>
        <app-paid-table [entries]="p.paid" />
      }
      @if (p.included.length === 0 && p.paid.length === 0) {
        <p class="text-muted">Nothing available for this provider yet.</p>
      }
    }
  `,
})
export class ProviderPage {
  private readonly route = inject(ActivatedRoute);
  private readonly api = inject(ProviderApi);

  protected readonly page = signal<ProviderPageDto | null>(null);
  protected readonly label = signal<string>('');
  protected readonly loading = signal(true);
  protected readonly error = signal<string | null>(null);

  constructor() {
    this.route.paramMap.pipe(takeUntilDestroyed()).subscribe((params) => {
      const key = params.get('key') ?? '';
      this.load(key);
    });
  }

  private load(key: string): void {
    this.loading.set(true);
    this.error.set(null);
    this.page.set(null);
    this.label.set(PROVIDERS.find((p) => p.key === key)?.label ?? key);
    this.api.getProvider(key).subscribe({
      next: (page) => {
        this.page.set(page);
        this.loading.set(false);
      },
      error: () => {
        this.error.set(`Failed to load "${key}".`);
        this.loading.set(false);
      },
    });
  }
}
