import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { CatalogApi } from '../../core/api/catalog-api';
import { ImdbId } from '../../core/domain';
import { UserPrefsStore } from '../../core/user-prefs-store';
import { OverviewEntry } from '../../core/models';
import { SeenStore } from '../../core/seen-store';
import { overviewToTile } from '../../core/tile-entry';
import { CatalogTable } from '../../shared/catalog-table/catalog-table';
import { ErrorAlert } from '../../shared/error-alert/error-alert';
import { TitleGrid } from '../../shared/title-grid/title-grid';
import { ViewToggleButton } from '../../shared/view-toggle-button/view-toggle-button';
import { TranslocoService } from '@jsverse/transloco';

/** Container: loads the catalogue overview and hands it to the presentational table/grid. */
@Component({
  selector: 'app-overview-page',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [CatalogTable, TitleGrid, ViewToggleButton, ErrorAlert],
  template: `
    <h1>Where 2 Stream</h1>
    <app-view-toggle-button />
    @if (error()) {
      <app-error-alert [message]="error()" />
    } @else if (userPrefs.viewMode() === 'GRID') {
      <app-title-grid
        [entries]="tileEntries()"
        [loading]="loading()"
        [recentlyChangedId]="seenStore.recentlyChanged()"
        (seenToggle)="onSeenToggle($event)" />
    } @else {
      <app-catalog-table
        [entries]="entries()"
        [loading]="loading()"
        [recentlyChangedId]="seenStore.recentlyChanged()"
        (seenToggle)="onSeenToggle($event)" />
    }
  `,
})
export class OverviewPage {
  private readonly api = inject(CatalogApi);
  protected readonly seenStore = inject(SeenStore);
  protected readonly userPrefs = inject(UserPrefsStore);
  private readonly transloco = inject(TranslocoService);

  protected readonly entries = signal<OverviewEntry[]>([]);
  protected readonly tileEntries = computed(() => this.entries().map(overviewToTile));
  protected readonly loading = signal(true);
  protected readonly error = signal<string | null>(null);

  protected onSeenToggle({ imdbId, seen }: { imdbId: ImdbId; seen: boolean }): void {
    this.seenStore.toggle(imdbId, seen, (s) =>
      this.entries.update((list) =>
        list.map((e) => (e.imdbId === imdbId ? { ...e, isRated: s } : e)),
      ),
    );
  }

  constructor() {
    this.api.getCatalog().subscribe({
      next: (entries) => {
        this.entries.set(entries);
        this.loading.set(false);
      },
      error: () => {
        this.error.set(this.transloco.translate('overview.loadError'));
        this.loading.set(false);
      },
    });
  }
}
