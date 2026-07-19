import { provideHttpClient, withFetch } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute, convertToParamMap, ParamMap } from '@angular/router';
import { BehaviorSubject } from 'rxjs';
import { ProviderPage } from './provider-page';
import { ProviderPage as ProviderPageDto } from '../../core/models';

describe('ProviderPage', () => {
  let fixture: ComponentFixture<ProviderPage>;
  let httpMock: HttpTestingController;
  let paramMap: BehaviorSubject<ParamMap>;

  function setup(initialKey: string) {
    paramMap = new BehaviorSubject<ParamMap>(convertToParamMap({ key: initialKey }));
    TestBed.configureTestingModule({
      imports: [ProviderPage],
      providers: [
        provideHttpClient(withFetch()),
        provideHttpClientTesting(),
        { provide: ActivatedRoute, useValue: { paramMap: paramMap.asObservable() } },
      ],
    });
    fixture = TestBed.createComponent(ProviderPage);
    httpMock = TestBed.inject(HttpTestingController);
  }

  afterEach(() => httpMock.verify());

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
      .flush(page({ included: [{ isRated: false, name: 'Nolan Film', imdbId: 'tt9', year: 2020, added: '2020-01-01' }] }));
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('h1').textContent).toContain('Netflix');
    expect(fixture.nativeElement.querySelector('app-flatrate-table')).not.toBeNull();
    expect(fixture.nativeElement.textContent).toContain('Nolan Film');
  });

  it('reloads when the route param changes to another provider', () => {
    setup('netflix');
    httpMock.expectOne((r) => r.url.endsWith('/api/providers/netflix')).flush(page({}));
    fixture.detectChanges();

    paramMap.next(convertToParamMap({ key: 'google' }));
    httpMock
      .expectOne((r) => r.url.endsWith('/api/providers/google'))
      .flush(page({ provider: 'google', paid: [
        { name: 'Buyable', imdbId: 'tt5', price: 'kaufen: HD: 9,99 ', added: '2021-01-01', isRated: false, year: '2021', languages: null },
      ] }));
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('h1').textContent).toContain('Google Play');
    expect(fixture.nativeElement.querySelector('app-paid-table')).not.toBeNull();
  });

  it('shows an empty-state message when nothing is available', () => {
    setup('wow');
    httpMock.expectOne((r) => r.url.endsWith('/api/providers/wow')).flush(page({ provider: 'wow' }));
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('Nothing available');
  });
});
