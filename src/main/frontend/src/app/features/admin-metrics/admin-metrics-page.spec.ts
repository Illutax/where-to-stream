import { provideHttpClient, withFetch } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { InstanceMetrics } from '../../core/models';
import { translocoTesting } from '../../testing/transloco-testing';
import { AdminMetricsPage } from './admin-metrics-page';

describe('AdminMetricsPage', () => {
  let fixture: ComponentFixture<AdminMetricsPage>;
  let httpMock: HttpTestingController;

  const metrics = (overrides: Partial<InstanceMetrics> = {}): InstanceMetrics => ({
    users: 3,
    distinctTitles: 200,
    watchlistEntries: 260,
    metadataRows: 150,
    metadataWithoutData: 20,
    posterRows: 180,
    postersWithImage: 120,
    availabilityTitles: 190,
    availabilityStale: 12,
    ...overrides,
  });

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [AdminMetricsPage, translocoTesting()],
      providers: [provideHttpClient(withFetch()), provideHttpClientTesting()],
    });
    fixture = TestBed.createComponent(AdminMetricsPage);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpMock.verify());

  function load(dto: InstanceMetrics = metrics()): void {
    fixture.detectChanges();
    httpMock.expectOne((r) => r.url.endsWith('/api/admin/metrics') && r.method === 'GET').flush(dto);
    fixture.detectChanges();
  }

  const tiles = () => Array.from(fixture.nativeElement.querySelectorAll('.metric-tile')) as HTMLElement[];
  const tileWith = (label: string) => tiles().find((t) => t.textContent?.includes(label));

  it('shows skeleton tiles first and the numbers once loaded', () => {
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelectorAll('.skeleton-bar').length).toBeGreaterThan(0);

    httpMock.expectOne((r) => r.url.endsWith('/api/admin/metrics')).flush(metrics());
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelectorAll('.skeleton-bar').length).toBe(0);
    expect(tileWith('User accounts')?.querySelector('.metric-value')?.textContent?.trim()).toBe('3');
  });

  it('reports posters that actually carry an image, with the negative rows named separately', () => {
    // The tile must not read "180 posters cached" when 60 of those rows mean "this title has none".
    load();

    const tile = tileWith('Posters cached');
    expect([
      tile?.querySelector('.metric-value')?.textContent?.trim(),
      tile?.querySelector('.metric-detail')?.textContent?.trim(),
    ]).toEqual(['120', 'plus 60 row(s) recording that there is nothing to cache']);
  });

  it('derives poster coverage from titles rather than from cache rows', () => {
    load();

    expect(tileWith('poster coverage')?.querySelector('.metric-value')?.textContent?.trim()).toBe('60');
  });

  it('does not divide by zero on an empty instance', () => {
    load(metrics({ distinctTitles: 0, postersWithImage: 0 }));

    expect(tileWith('poster coverage')?.querySelector('.metric-value')?.textContent?.trim()).toBe('0');
  });

  it('says so plainly when nothing is stale, instead of showing a bare zero', () => {
    load(metrics({ availabilityStale: 0 }));

    expect(tileWith('Stale, awaiting re-scrape')?.querySelector('.metric-detail')?.textContent?.trim())
      .toBe('Everything cached is current.');
  });

  it('shows an error instead of empty tiles when the request fails', () => {
    fixture.detectChanges();
    httpMock
      .expectOne((r) => r.url.endsWith('/api/admin/metrics'))
      .flush(null, { status: 403, statusText: 'Forbidden' });
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('.error-alert')?.textContent).toContain(
      'Could not load the instance metrics.',
    );
    expect(tiles()).toHaveLength(0);
  });
});
