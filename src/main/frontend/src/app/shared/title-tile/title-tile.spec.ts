import { provideHttpClient, withFetch } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { imdbId, watchlistDate } from '../../core/domain';
import { translocoTesting } from '../../testing/transloco-testing';
import { TitleTile } from './title-tile';

describe('TitleTile', () => {
  let fixture: ComponentFixture<TitleTile>;
  let httpMock: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [TitleTile, translocoTesting()],
      providers: [provideHttpClient(withFetch()), provideHttpClientTesting()],
    });
    fixture = TestBed.createComponent(TitleTile);
    fixture.componentRef.setInput('imdbId', imdbId('tt1'));
    fixture.componentRef.setInput('name', 'Old School - Wir lassen absolut nichts anbrennen');
    fixture.componentRef.setInput('year', '2003');
    fixture.componentRef.setInput('added', watchlistDate('2026-07-02'));
    fixture.componentRef.setInput('isRated', false);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpMock.verify());

  const poster = () => fixture.nativeElement.querySelector('.poster-img') as HTMLImageElement | null;
  const mainTitle = () => fixture.nativeElement.querySelector('.main-title a') as HTMLAnchorElement;
  const subtitle = () => fixture.nativeElement.querySelector('.subtitle') as HTMLElement | null;
  const toggle = () => fixture.nativeElement.querySelector('.watched-toggle') as HTMLButtonElement;

  it('renders the poster, splits the title into main/subtitle, and shows the year', () => {
    fixture.detectChanges();
    httpMock.expectOne((r) => r.url.endsWith('/api/titles/tt1/meta')).flush({ rating: null, germanTitle: null });
    fixture.detectChanges();

    expect(poster()?.getAttribute('src')).toContain('/api/titles/tt1/poster/full');
    expect(mainTitle().textContent?.trim()).toBe('Old School');
    expect(mainTitle().getAttribute('href')).toBe('https://www.imdb.com/title/tt1');
    expect(subtitle()?.textContent?.trim()).toBe('Wir lassen absolut nichts anbrennen');
    expect(fixture.nativeElement.querySelector('.year-chip').textContent.trim()).toBe('2003');
  });

  it('hides the subtitle element when the title has no separator', () => {
    fixture.componentRef.setInput('name', 'Vertigo');
    fixture.detectChanges();
    httpMock.expectOne((r) => r.url.endsWith('/api/titles/tt1/meta')).flush({ rating: null, germanTitle: null });
    fixture.detectChanges();

    expect(mainTitle().textContent?.trim()).toBe('Vertigo');
    expect(subtitle()).toBeNull();
  });

  it('hides the poster and falls back to the placeholder box on image error', () => {
    fixture.detectChanges();
    httpMock.expectOne((r) => r.url.endsWith('/api/titles/tt1/meta')).flush({ rating: null, germanTitle: null });
    fixture.detectChanges();

    poster()!.dispatchEvent(new Event('error'));
    fixture.detectChanges();

    expect(poster()).toBeNull();
    expect(mainTitle().textContent?.trim()).toBe('Old School'); // title/scrim/badges stay intact
  });

  it('emits a seen toggle to the opposite of the current flag when the watched button is clicked', () => {
    fixture.detectChanges();
    httpMock.expectOne((r) => r.url.endsWith('/api/titles/tt1/meta')).flush({ rating: null, germanTitle: null });
    fixture.detectChanges();

    let emitted: { imdbId: string; seen: boolean } | undefined;
    fixture.componentInstance.seenToggle.subscribe((e) => (emitted = e));
    toggle().click();

    expect(emitted).toEqual({ imdbId: 'tt1', seen: true });
  });

  it('reflects isRated via aria-pressed and the watched CSS class', () => {
    fixture.componentRef.setInput('isRated', true);
    fixture.detectChanges();
    httpMock.expectOne((r) => r.url.endsWith('/api/titles/tt1/meta')).flush({ rating: null, germanTitle: null });
    fixture.detectChanges();

    expect(toggle().getAttribute('aria-pressed')).toBe('true');
    expect(toggle().classList.contains('watched')).toBe(true);
  });

  it('shows the age badge only when age ratings are on and a rating is present', () => {
    fixture.detectChanges();
    httpMock
      .expectOne((r) => r.url.endsWith('/api/titles/tt1/meta'))
      .flush({ rating: { system: 'FSK', label: '16' }, germanTitle: null });
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('.age-badge')?.textContent?.trim()).toBe('16');
  });
});
