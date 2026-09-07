import { provideHttpClient, withFetch } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ChangeDetectionStrategy, Component, input } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ImdbId, imdbId } from './domain';
import { injectTitleMeta } from './title-meta';
import { UserPrefsStore } from './user-prefs-store';

// A throwaway host so `injectTitleMeta` (a functional-injection helper) runs inside a real
// component lifecycle, exactly like its real callers (TitleCell, TitleTile, EbayLink) do.
@Component({
  selector: 'app-title-meta-host',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: '',
})
class TitleMetaHost {
  readonly imdbId = input.required<ImdbId>();
  readonly meta = injectTitleMeta(() => this.imdbId());
}

describe('injectTitleMeta', () => {
  let fixture: ComponentFixture<TitleMetaHost>;
  let httpMock: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [TitleMetaHost],
      providers: [provideHttpClient(withFetch()), provideHttpClientTesting()],
    });
    fixture = TestBed.createComponent(TitleMetaHost);
    fixture.componentRef.setInput('imdbId', imdbId('tt1'));
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpMock.verify());

  it('fetches the metadata once age ratings are on (the default)', () => {
    fixture.detectChanges();

    httpMock
      .expectOne((r) => r.url.endsWith('/api/titles/tt1/meta'))
      .flush({ rating: { system: 'FSK', label: '12' }, germanTitle: 'Titel' });
    fixture.detectChanges();

    expect(fixture.componentInstance.meta()).toEqual({ rating: { system: 'FSK', label: '12' }, germanTitle: 'Titel' });
  });

  it('does not fetch when both preferences are off', () => {
    TestBed.inject(UserPrefsStore).init({ showAgeRatings: false, showGermanTitle: false });

    fixture.detectChanges();

    httpMock.expectNone((r) => r.url.includes('/meta'));
    expect(fixture.componentInstance.meta()).toBeNull();
  });

  it('sets meta to null on a fetch error', () => {
    fixture.detectChanges();

    httpMock.expectOne((r) => r.url.endsWith('/api/titles/tt1/meta')).error(new ProgressEvent('error'));
    fixture.detectChanges();

    expect(fixture.componentInstance.meta()).toBeNull();
  });

  it('serves several components showing the same title from one request', () => {
    // A dashboard row has two of them since the eBay column arrived (TODO-57): the title cell and
    // the search link. Per-component signals would have doubled a request count that is already
    // one per row (TODO-59) -- the column would have paid for itself in traffic.
    const second = TestBed.createComponent(TitleMetaHost);
    second.componentRef.setInput('imdbId', imdbId('tt1'));

    fixture.detectChanges();
    second.detectChanges();

    httpMock.expectOne((r) => r.url.endsWith('/api/titles/tt1/meta'))
      .flush({ rating: null, germanTitle: 'Der Piano-Spieler' });
    fixture.detectChanges();
    second.detectChanges();

    // expectOne above already fails on a second request; this pins that both hosts see the answer.
    expect([fixture.componentInstance.meta()?.germanTitle, second.componentInstance.meta()?.germanTitle])
      .toEqual(['Der Piano-Spieler', 'Der Piano-Spieler']);
  });

  it('lets the next component retry a title whose fetch failed', () => {
    // Only *answers* are shared, not failures. Remembering the failure would be cheaper, but it
    // would let one transient blip hide a badge for the rest of the session; a broken title now
    // costs what it always cost, one request per consumer.
    fixture.detectChanges();
    httpMock.expectOne((r) => r.url.endsWith('/api/titles/tt1/meta')).error(new ProgressEvent('error'));

    const second = TestBed.createComponent(TitleMetaHost);
    second.componentRef.setInput('imdbId', imdbId('tt1'));
    second.detectChanges();

    httpMock.expectOne((r) => r.url.endsWith('/api/titles/tt1/meta'))
      .flush({ rating: null, germanTitle: 'Zweiter Versuch' });
    second.detectChanges();

    expect(second.componentInstance.meta()?.germanTitle).toBe('Zweiter Versuch');
  });
});
