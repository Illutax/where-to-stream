import { provideHttpClient, withFetch } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute, convertToParamMap, ParamMap } from '@angular/router';
import { By } from '@angular/platform-browser';
import { BehaviorSubject } from 'rxjs';
import { ProviderPage } from './provider-page';
import { ImdbId, imdbId, releaseYear, watchlistDate } from '../../core/domain';
import { ProviderPage as ProviderPageDto } from '../../core/models';
import { SeenStore } from '../../core/seen-store';
import { FlatrateTable } from '../../shared/flatrate-table/flatrate-table';

describe('ProviderPage', () => {
  let fixture: ComponentFixture<ProviderPage>;
  let httpMock: HttpTestingController;
  let paramMap: BehaviorSubject<ParamMap>;
  let toggled: { imdbId: string; seen: boolean } | undefined;

  function setup(initialKey: string) {
    toggled = undefined;
    const seenStore = {
      recentlyChanged: () => null,
      toggle: (id: ImdbId, seen: boolean, apply: (s: boolean) => void) => {
        toggled = { imdbId: id, seen };
        apply(seen);
      },
    };
    paramMap = new BehaviorSubject<ParamMap>(convertToParamMap({ key: initialKey }));
    TestBed.configureTestingModule({
      imports: [ProviderPage],
      providers: [
        provideHttpClient(withFetch()),
        provideHttpClientTesting(),
        { provide: ActivatedRoute, useValue: { paramMap: paramMap.asObservable() } },
        { provide: SeenStore, useValue: seenStore },
      ],
    });
    fixture = TestBed.createComponent(ProviderPage);
    httpMock = TestBed.inject(HttpTestingController);
  }

  afterEach(() => {
    // The per-row age-rating badges fetch independently; drain those before verifying.
    httpMock.match((r) => r.url.includes('/rating')).forEach((req) => req.flush(null, { status: 404, statusText: 'Not Found' }));
    httpMock.verify();
  });

  const page = (over: Partial<ProviderPageDto>): ProviderPageDto => ({
    provider: 'netflix',
    included: [],
    paid: [],
    ...over,
  });

  it('loads the provider named by the route param and shows its label', () => {
    setup('netflix');
    httpMock
      .expectOne((r) => r.url.endsWith('/api/providers/netflix'))
      .flush(page({ included: [{ isRated: false, name: 'Nolan Film', imdbId: imdbId('tt9'), year: releaseYear(2020), added: watchlistDate('2020-01-01') }] }));
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('h1').textContent).toContain('Netflix');
    expect(fixture.nativeElement.querySelector('app-flatrate-table')).not.toBeNull();
    expect(fixture.nativeElement.textContent).toContain('Nolan Film');
  });

  it('reloads when the route param changes to another provider', () => {
    setup('netflix');
    httpMock.expectOne((r) => r.url.endsWith('/api/providers/netflix')).flush(page({}));
    fixture.detectChanges();

    paramMap.next(convertToParamMap({ key: 'youtube' }));
    httpMock
      .expectOne((r) => r.url.endsWith('/api/providers/youtube'))
      .flush(page({ provider: 'youtube', paid: [
        { name: 'Buyable', imdbId: imdbId('tt5'), price: 'kaufen: HD: 9,99 ', added: watchlistDate('2021-01-01'), isRated: false, year: '2021', languages: null },
      ] }));
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('h1').textContent).toContain('Youtube Store');
    expect(fixture.nativeElement.querySelector('app-paid-table')).not.toBeNull();
  });

  it('delegates a seen toggle from the flatrate table to the store and flips the row', () => {
    setup('netflix');
    httpMock
      .expectOne((r) => r.url.endsWith('/api/providers/netflix'))
      .flush(page({ included: [{ isRated: false, name: 'Nolan Film', imdbId: imdbId('tt9'), year: releaseYear(2020), added: watchlistDate('2020-01-01') }] }));
    fixture.detectChanges();

    const table = fixture.debugElement.query(By.directive(FlatrateTable)).componentInstance as FlatrateTable;
    table.seenToggle.emit({ imdbId: imdbId('tt9'), seen: true });
    fixture.detectChanges();

    expect(toggled).toEqual({ imdbId: 'tt9', seen: true });
    expect(fixture.nativeElement.querySelector('tbody .seen-toggle').textContent).toContain('✅');
  });

  it('shows an empty-state message when nothing is available', () => {
    setup('wow');
    httpMock.expectOne((r) => r.url.endsWith('/api/providers/wow')).flush(page({ provider: 'wow' }));
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('Nothing available');
  });
});
