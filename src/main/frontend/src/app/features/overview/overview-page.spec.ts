import { provideHttpClient, withFetch } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { OverviewPage } from './overview-page';
import { OverviewEntry } from '../../core/models';

describe('OverviewPage', () => {
  let fixture: ComponentFixture<OverviewPage>;
  let httpMock: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [OverviewPage],
      providers: [provideHttpClient(withFetch()), provideHttpClientTesting()],
    });
    fixture = TestBed.createComponent(OverviewPage); // constructor kicks off the load
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpMock.verify());

  it('shows the loading indicator until the catalogue resolves', () => {
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('app-loading')).not.toBeNull();
    httpMock.expectOne((r) => r.url.endsWith('/api/catalog')).flush([]);
  });

  it('renders the catalogue table on success', () => {
    const payload: OverviewEntry[] = [
      { isRated: true, name: 'Movie', imdbId: 'tt1', year: 2020, added: '2020-01-01', services: 'Netflix' },
    ];
    httpMock.expectOne((r) => r.url.endsWith('/api/catalog')).flush(payload);
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('app-loading')).toBeNull();
    expect(fixture.nativeElement.querySelectorAll('tbody tr')).toHaveLength(1);
    expect(fixture.nativeElement.textContent).toContain('Movie');
  });

  it('shows an error alert when the request fails', () => {
    httpMock
      .expectOne((r) => r.url.endsWith('/api/catalog'))
      .flush('boom', { status: 500, statusText: 'Server Error' });
    fixture.detectChanges();

    const alert = fixture.nativeElement.querySelector('.error-alert');
    expect(alert).not.toBeNull();
    expect(alert.textContent).toContain('Failed to load the catalogue');
  });
});
