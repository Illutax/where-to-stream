import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { ActivatedRoute } from '@angular/router';
import { ProviderApi } from '../../core/api/provider-api';
import { ImdbId } from '../../core/domain';
import { GridPrefsStore } from '../../core/grid-prefs-store';
import { PROVIDERS, ProviderPage as ProviderPageDto } from '../../core/models';
import { SeenStore } from '../../core/seen-store';
import { flatrateToTile, paidToTile } from '../../core/tile-entry';
import { ErrorAlert } from '../../shared/error-alert/error-alert';
import { FlatrateTable } from '../../shared/flatrate-table/flatrate-table';
import { Loading } from '../../shared/loading/loading';
import { PaidTable } from '../../shared/paid-table/paid-table';
import { TitleGrid } from '../../shared/title-grid/title-grid';
import { ViewToggleButton } from '../../shared/view-toggle-button/view-toggle-button';
import { TranslocoPipe, TranslocoService } from '@jsverse/transloco';

/**
 * Container for all five provider pages. Reacts to the {@code :key} route param (the same
 * component instance is reused when navigating between providers) and renders the flatrate
 * and/or paid tables (or their grid equivalents) depending on what the server returns.
 */
@Component({
  selector: 'app-provider-page',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [FlatrateTable, PaidTable, TitleGrid, ViewToggleButton, Loading, ErrorAlert, TranslocoPipe],
  template: `
    <h1>{{ label() }}</h1>
    <app-view-toggle-button />
    @if (loading()) {
      <app-loading />
    } @else if (error()) {
      <app-error-alert [message]="error()" />
    } @else if (page(); as p) {
      @if (p.included.length > 0) {
        <h2>{{ 'provider.included' | transloco }}</h2>
        @if (gridPrefs.viewMode() === 'GRID') {
          <app-title-grid
            [entries]="includedTiles()"
            [recentlyChangedId]="seenStore.recentlyChanged()"
            (seenToggle)="onSeenToggle($event)" />
        } @else {
          <app-flatrate-table
            [entries]="p.included"
            [recentlyChangedId]="seenStore.recentlyChanged()"
            (seenToggle)="onSeenToggle($event)" />
        }
      }
      @if (p.paid.length > 0) {
        <h2>{{ 'provider.buyRent' | transloco }}</h2>
        @if (gridPrefs.viewMode() === 'GRID') {
          <app-title-grid
            [entries]="paidTiles()"
            [recentlyChangedId]="seenStore.recentlyChanged()"
            (seenToggle)="onSeenToggle($event)" />
        } @else {
          <app-paid-table
            [entries]="p.paid"
            [recentlyChangedId]="seenStore.recentlyChanged()"
            (seenToggle)="onSeenToggle($event)" />
        }
      }
      @if (p.included.length === 0 && p.paid.length === 0) {
        <p class="text-muted">{{ 'provider.empty' | transloco }}</p>
      }
    }
  `,
})
export class ProviderPage {
  private readonly route = inject(ActivatedRoute);
  private readonly api = inject(ProviderApi);
  protected readonly seenStore = inject(SeenStore);
  protected readonly gridPrefs = inject(GridPrefsStore);
  private readonly transloco = inject(TranslocoService);

  protected readonly page = signal<ProviderPageDto | null>(null);
  protected readonly includedTiles = computed(() => this.page()?.included.map(flatrateToTile) ?? []);
  protected readonly paidTiles = computed(() => this.page()?.paid.map(paidToTile) ?? []);
  protected readonly label = signal<string>('');
  protected readonly loading = signal(true);
  protected readonly error = signal<string | null>(null);

  protected onSeenToggle({ imdbId, seen }: { imdbId: ImdbId; seen: boolean }): void {
    // A title can be in the "included" and/or the "paid" list (and paid may repeat it per language
    // variant); flip the flag wherever this imdbId appears.
    this.seenStore.toggle(imdbId, seen, (s) =>
      this.page.update((p) =>
        p === null
          ? p
          : {
              ...p,
              included: p.included.map((e) => (e.imdbId === imdbId ? { ...e, isRated: s } : e)),
              paid: p.paid.map((e) => (e.imdbId === imdbId ? { ...e, isRated: s } : e)),
            },
      ),
    );
  }

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
        this.error.set(this.transloco.translate('provider.loadError', { provider: key }));
        this.loading.set(false);
      },
    });
  }
}
