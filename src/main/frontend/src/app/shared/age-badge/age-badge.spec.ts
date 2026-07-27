import { provideHttpClient, withFetch } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { AgeBadge } from './age-badge';
import { AgeRatingStore } from '../../core/age-rating-store';
import { imdbId } from '../../core/domain';

describe('AgeBadge', () => {
  let fixture: ComponentFixture<AgeBadge>;
  let httpMock: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [AgeBadge],
      providers: [provideHttpClient(withFetch()), provideHttpClientTesting()],
    });
    fixture = TestBed.createComponent(AgeBadge);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpMock.verify());

  const badge = () => fixture.nativeElement.querySelector('.age-badge') as HTMLElement | null;

  it('renders the FSK label with its colour class', () => {
    fixture.componentRef.setInput('imdbId', imdbId('tt1'));
    fixture.detectChanges();

    httpMock.expectOne((r) => r.url.endsWith('/api/titles/tt1/rating')).flush({ system: 'FSK', label: '16' });
    fixture.detectChanges();

    expect(badge()).not.toBeNull();
    expect(badge()!.textContent?.trim()).toBe('16');
    expect(badge()!.classList).toContain('age-badge--fsk-16');
  });

  it('uses the neutral class for a non-FSK (fallback) rating', () => {
    fixture.componentRef.setInput('imdbId', imdbId('tt2'));
    fixture.detectChanges();

    httpMock.expectOne((r) => r.url.endsWith('/api/titles/tt2/rating')).flush({ system: 'OTHER', label: 'R' });
    fixture.detectChanges();

    expect(badge()!.textContent?.trim()).toBe('R');
    expect(badge()!.classList).toContain('age-badge--other');
  });

  it('shows nothing when the title has no rating (404)', () => {
    fixture.componentRef.setInput('imdbId', imdbId('tt404'));
    fixture.detectChanges();

    httpMock.expectOne((r) => r.url.endsWith('/api/titles/tt404/rating'))
      .flush(null, { status: 404, statusText: 'Not Found' });
    fixture.detectChanges();

    expect(badge()).toBeNull();
  });

  it('does not render or fetch when the preference is off', () => {
    TestBed.inject(AgeRatingStore).init(false);
    fixture.componentRef.setInput('imdbId', imdbId('tt1'));
    fixture.detectChanges();

    httpMock.expectNone((r) => r.url.includes('/rating'));
    expect(badge()).toBeNull();
  });
});
