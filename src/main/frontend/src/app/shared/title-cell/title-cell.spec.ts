import { provideHttpClient, withFetch } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { TitleCell } from './title-cell';
import { AgeRatingStore } from '../../core/age-rating-store';
import { GermanTitleStore } from '../../core/german-title-store';
import { imdbId } from '../../core/domain';

describe('TitleCell', () => {
  let fixture: ComponentFixture<TitleCell>;
  let httpMock: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [TitleCell],
      providers: [provideHttpClient(withFetch()), provideHttpClientTesting()],
    });
    fixture = TestBed.createComponent(TitleCell);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpMock.verify());

  const link = () => fixture.nativeElement.querySelector('a') as HTMLAnchorElement;
  const badge = () => fixture.nativeElement.querySelector('.age-badge') as HTMLElement | null;

  it('shows the English name and the FSK badge when age ratings are on', () => {
    // AgeRatingStore defaults on → the cell fetches its metadata.
    fixture.componentRef.setInput('imdbId', imdbId('tt1'));
    fixture.componentRef.setInput('name', 'The Godfather');
    fixture.detectChanges();

    httpMock.expectOne((r) => r.url.endsWith('/api/titles/tt1/meta'))
      .flush({ rating: { system: 'FSK', label: '16' }, germanTitle: 'Der Pate' });
    fixture.detectChanges();

    expect(link().textContent?.trim()).toBe('The Godfather'); // German-title toggle off → English
    expect(badge()?.textContent?.trim()).toBe('16');
  });

  it('shows the German title when that preference is on and one exists', () => {
    TestBed.inject(GermanTitleStore).init(true);
    TestBed.inject(AgeRatingStore).init(false); // only the German-title preference is on
    fixture.componentRef.setInput('imdbId', imdbId('tt1'));
    fixture.componentRef.setInput('name', 'Up');
    fixture.detectChanges();

    httpMock.expectOne((r) => r.url.endsWith('/api/titles/tt1/meta'))
      .flush({ rating: null, germanTitle: 'Oben' });
    fixture.detectChanges();

    expect(link().textContent?.trim()).toBe('Oben');
    expect(badge()).toBeNull(); // age ratings off
  });

  it('does not fetch or change the title when both preferences are off', () => {
    TestBed.inject(AgeRatingStore).init(false);
    TestBed.inject(GermanTitleStore).init(false);
    fixture.componentRef.setInput('imdbId', imdbId('tt1'));
    fixture.componentRef.setInput('name', 'Up');
    fixture.detectChanges();

    httpMock.expectNone((r) => r.url.includes('/meta'));
    expect(link().textContent?.trim()).toBe('Up');
  });
});
