import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { TranslocoPipe, TranslocoService } from '@jsverse/transloco';
import { MetricsApi } from '../../core/api/metrics-api';
import { InstanceMetrics } from '../../core/models';
import { ErrorAlert } from '../../shared/error-alert/error-alert';
import { MetricTile } from '../../shared/metric-tile/metric-tile';

/**
 * ADMIN-only dashboard describing this deployment: how much it holds and how healthy its caches
 * are.
 *
 * <p>Grouped into three sections rather than one wall of numbers, because the figures answer
 * different questions — how big is the instance, how well are the title caches filled, and is the
 * availability data current. The middle group is the one worth reading twice: a cache row that
 * records "there is nothing here" is still a row, so each cache tile carries the share of it that
 * is an absence.
 */
@Component({
  selector: 'app-admin-metrics-page',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [MetricTile, ErrorAlert, TranslocoPipe],
  template: `
    <h1>{{ 'metrics.title' | transloco }}</h1>
    <p class="text-muted">{{ 'metrics.intro' | transloco }}</p>
    <app-error-alert [message]="error()" />

    @if (!error()) {
      <h2>{{ 'metrics.sizeHeading' | transloco }}</h2>
      <div class="metric-grid">
        <app-metric-tile
          [label]="'metrics.users' | transloco"
          [value]="metrics()?.users ?? null"
          [loading]="loading()" />
        <app-metric-tile
          [label]="'metrics.titles' | transloco"
          [value]="metrics()?.distinctTitles ?? null"
          [detail]="entriesDetail()"
          [loading]="loading()" />
        <app-metric-tile
          [label]="'metrics.watchlistEntries' | transloco"
          [value]="metrics()?.watchlistEntries ?? null"
          [loading]="loading()" />
      </div>

      <h2>{{ 'metrics.cacheHeading' | transloco }}</h2>
      <div class="metric-grid">
        <app-metric-tile
          [label]="'metrics.posters' | transloco"
          [value]="metrics()?.postersWithImage ?? null"
          [detail]="posterDetail()"
          [loading]="loading()" />
        <app-metric-tile
          [label]="'metrics.posterCoverage' | transloco"
          [value]="posterCoverage()"
          [detail]="coverageDetail()"
          [loading]="loading()" />
        <app-metric-tile
          [label]="'metrics.metadata' | transloco"
          [value]="metadataWithData()"
          [detail]="metadataDetail()"
          [loading]="loading()" />
      </div>

      <h2>{{ 'metrics.availabilityHeading' | transloco }}</h2>
      <div class="metric-grid">
        <app-metric-tile
          [label]="'metrics.availabilityTitles' | transloco"
          [value]="metrics()?.availabilityTitles ?? null"
          [loading]="loading()" />
        <app-metric-tile
          [label]="'metrics.availabilityStale' | transloco"
          [value]="metrics()?.availabilityStale ?? null"
          [detail]="staleDetail()"
          [loading]="loading()" />
      </div>
    }
  `,
  styles: `
    /* auto-fit + minmax is the whole responsive story: one column on a phone, as many as fit on a
       desktop, with no breakpoints to keep in sync with the Material ones. The 13rem floor is the
       width at which a six-figure number and its label still fit on one line. */
    .metric-grid {
      display: grid;
      grid-template-columns: repeat(auto-fit, minmax(13rem, 1fr));
      gap: 1rem;
      margin-bottom: 2rem;
    }
    h2 {
      font: var(--mat-sys-title-medium);
      margin-bottom: 0.75rem;
    }
  `,
})
export class AdminMetricsPage {
  private readonly api = inject(MetricsApi);
  private readonly transloco = inject(TranslocoService);

  protected readonly metrics = signal<InstanceMetrics | null>(null);
  protected readonly loading = signal(true);
  protected readonly error = signal<string | null>(null);

  /** Rows carrying an actual poster, against the titles that could have one. */
  protected readonly posterCoverage = computed(() => {
    const m = this.metrics();
    if (!m || m.distinctTitles === 0) {
      return 0;
    }
    return Math.round((m.postersWithImage / m.distinctTitles) * 100);
  });

  protected readonly metadataWithData = computed(() => {
    const m = this.metrics();
    return m ? m.metadataRows - m.metadataWithoutData : null;
  });

  protected readonly entriesDetail = computed(() =>
    this.detail('metrics.acrossLists', { entries: this.metrics()?.watchlistEntries ?? 0 }),
  );

  /** Derived rather than sent: the DTO carries total rows and rows with an image, and one
   *  subtraction here beats a third field that could disagree with the other two. */
  protected readonly posterDetail = computed(() => {
    const m = this.metrics();
    return this.detail('metrics.negativeRows', { count: m ? m.posterRows - m.postersWithImage : 0 });
  });

  protected readonly metadataDetail = computed(() =>
    this.detail('metrics.negativeRows', { count: this.metrics()?.metadataWithoutData ?? 0 }),
  );

  protected readonly coverageDetail = computed(() => {
    const m = this.metrics();
    return m ? this.transloco.translate('metrics.coverageDetail', { titles: m.distinctTitles }) : null;
  });

  protected readonly staleDetail = computed(() => {
    const m = this.metrics();
    if (!m) {
      return null;
    }
    return m.availabilityStale === 0
      ? this.transloco.translate('metrics.allFresh')
      : this.transloco.translate('metrics.staleDetail');
  });

  constructor() {
    this.api.getMetrics().subscribe({
      next: (metrics) => {
        this.metrics.set(metrics);
        this.loading.set(false);
      },
      error: () => {
        this.error.set(this.transloco.translate('metrics.loadFailed'));
        this.loading.set(false);
      },
    });
  }

  private detail(key: string, params: Record<string, number>): string | null {
    return this.metrics() ? this.transloco.translate(key, params) : null;
  }
}
