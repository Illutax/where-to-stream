import { provideHttpClient, withFetch } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { TestbedHarnessEnvironment } from '@angular/cdk/testing/testbed';
import { MatButtonToggleGroupHarness } from '@angular/material/button-toggle/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { imdbId, watchlistDate } from '../../core/domain';
import { TileEntry } from '../../core/tile-entry';
import { translocoTesting } from '../../testing/transloco-testing';
import { TitleGrid } from './title-grid';

describe('TitleGrid', () => {
  let fixture: ComponentFixture<TitleGrid>;

  const entry = (over: Partial<TileEntry>): TileEntry => ({
    isRated: false,
    name: 'Movie',
    imdbId: imdbId('tt1'),
    year: '2020',
    added: watchlistDate('2020-01-01'),
    ...over,
  });

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [TitleGrid, translocoTesting()],
      providers: [provideHttpClient(withFetch()), provideHttpClientTesting()],
    });
    fixture = TestBed.createComponent(TitleGrid);
  });

  const tiles = () => Array.from(fixture.nativeElement.querySelectorAll('app-title-tile')) as HTMLElement[];
  const titles = () => tiles().map((t) => t.querySelector('.main-title')?.textContent?.trim());

  it('renders one tile per entry', () => {
    fixture.componentRef.setInput('entries', [
      entry({ name: 'Alpha', imdbId: imdbId('tt10') }),
      entry({ name: 'Beta', imdbId: imdbId('tt20') }),
    ]);
    fixture.detectChanges();

    expect(tiles()).toHaveLength(2);
  });

  it('renders an empty state when there are no entries', () => {
    fixture.componentRef.setInput('entries', []);
    fixture.detectChanges();

    expect(tiles()).toHaveLength(0);
    expect(fixture.nativeElement.textContent).toContain('No entries');
  });

  it('shows the watched count out of the total', () => {
    fixture.componentRef.setInput('entries', [
      entry({ imdbId: imdbId('tt1'), isRated: true }),
      entry({ imdbId: imdbId('tt2'), isRated: false }),
      entry({ imdbId: imdbId('tt3'), isRated: false }),
    ]);
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('.watched-counter').textContent).toContain('1 of 3 watched');
  });

  it('emits a seen toggle bubbled up from a tile', () => {
    fixture.componentRef.setInput('entries', [entry({ imdbId: imdbId('tt10'), isRated: false })]);
    fixture.detectChanges();

    let emitted: { imdbId: string; seen: boolean } | undefined;
    fixture.componentInstance.seenToggle.subscribe((e) => (emitted = e));
    (fixture.nativeElement.querySelector('.watched-toggle') as HTMLButtonElement).click();

    expect(emitted).toEqual({ imdbId: 'tt10', seen: true });
  });

  it('highlights only the recently-changed tile', () => {
    fixture.componentRef.setInput('entries', [entry({ imdbId: imdbId('tt1') }), entry({ imdbId: imdbId('tt2') })]);
    fixture.componentRef.setInput('recentlyChangedId', imdbId('tt1'));
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelectorAll('.recently-changed')).toHaveLength(1);
  });

  it('sorts by title ascending then descending via the sort toggle group', async () => {
    fixture.componentRef.setInput('entries', [
      entry({ name: 'Beta', imdbId: imdbId('tt2') }),
      entry({ name: 'Alpha', imdbId: imdbId('tt1') }),
    ]);
    fixture.detectChanges();

    const groups = await TestbedHarnessEnvironment.loader(fixture).getAllHarnesses(MatButtonToggleGroupHarness);
    const sortGroup = groups[0];
    const titleToggle = (await sortGroup.getToggles({ text: 'Title' }))[0];
    await titleToggle.check();
    fixture.detectChanges();
    expect(titles()).toEqual(['Alpha', 'Beta']);

    (fixture.nativeElement.querySelector('.sort-direction-toggle') as HTMLButtonElement).click();
    fixture.detectChanges();
    expect(titles()).toEqual(['Beta', 'Alpha']);
  });

  it('changes tiles-per-row via the per-row toggle group', async () => {
    fixture.componentRef.setInput('entries', [entry({ imdbId: imdbId('tt1') })]);
    fixture.detectChanges();

    const groups = await TestbedHarnessEnvironment.loader(fixture).getAllHarnesses(MatButtonToggleGroupHarness);
    const perRowGroup = groups[1];
    const three = (await perRowGroup.getToggles({ text: '3' }))[0];
    await three.check();
    fixture.detectChanges();

    expect(fixture.componentInstance['gridPrefs'].tilesPerRow()).toBe(3);
  });
});
