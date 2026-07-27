import { provideHttpClient, withFetch } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ChangeDetectionStrategy, Component, input } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { AgeRatingStore } from './age-rating-store';
import { GermanTitleStore } from './german-title-store';
import { ImdbId, imdbId } from './domain';
import { injectTitleMeta } from './title-meta';

// A throwaway host so `injectTitleMeta` (a functional-injection helper) runs inside a real
// component lifecycle, exactly like its two real callers (TitleCell, TitleTile) do.
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
    TestBed.inject(AgeRatingStore).init(false);
    TestBed.inject(GermanTitleStore).init(false);

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
});
