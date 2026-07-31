import { HarnessLoader } from '@angular/cdk/testing';
import { TestbedHarnessEnvironment } from '@angular/cdk/testing/testbed';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MatCheckboxHarness } from '@angular/material/checkbox/testing';
import { ManageTable } from './manage-table';
import { imdbId } from '../../core/domain';
import { ManageRow } from '../../core/models';
import { translocoTesting } from '../../testing/transloco-testing';

describe('ManageTable', () => {
  let fixture: ComponentFixture<ManageTable>;
  let component: ManageTable;
  let loader: HarnessLoader;

  const rows: ManageRow[] = [
    { imdbId: imdbId('tt1'), name: 'Alpha', isRated: true, needsScrape: true, lastScrapedAt: null },
    {
      imdbId: imdbId('tt2'),
      name: 'Beta',
      isRated: false,
      needsScrape: false,
      lastScrapedAt: '2026-07-15T00:00:00Z',
    },
  ];

  beforeEach(() => {
    TestBed.configureTestingModule({ imports: [ManageTable, translocoTesting()] });
    fixture = TestBed.createComponent(ManageTable);
    component = fixture.componentInstance;
    fixture.componentRef.setInput('rows', rows);
    fixture.componentRef.setInput('needsScrapeCount', 1);
    fixture.detectChanges();
    loader = TestbedHarnessEnvironment.loader(fixture);
  });

  const forms = () => Array.from(fixture.nativeElement.querySelectorAll('form')) as HTMLFormElement[];
  const invalidateButton = () => fixture.nativeElement.querySelector('.invalidate-button') as HTMLButtonElement;

  it('renders a checkbox per row and disables "Invalidate" until something is selected', async () => {
    const checkboxes = await loader.getAllHarnesses(MatCheckboxHarness);
    expect(checkboxes).toHaveLength(2);
    expect(invalidateButton().disabled).toBe(true);
  });

  it('enables "Invalidate" once a row is selected', async () => {
    const checkboxes = await loader.getAllHarnesses(MatCheckboxHarness);
    await checkboxes[0].check();
    fixture.detectChanges();
    expect(invalidateButton().disabled).toBe(false);
  });

  it('emits the selected ids on invalidate and then clears the selection', async () => {
    const emitted: string[][] = [];
    component.invalidate.subscribe((ids) => emitted.push(ids));

    const checkboxes = await loader.getAllHarnesses(MatCheckboxHarness);
    await checkboxes[0].check();
    await checkboxes[1].check();
    fixture.detectChanges();

    forms()[1].dispatchEvent(new Event('submit'));

    expect(emitted).toEqual([['tt1', 'tt2']]);
    fixture.detectChanges();
    expect(invalidateButton().disabled).toBe(true);
  });

  it('toggling a row off removes it from the selection', async () => {
    const emitted: string[][] = [];
    component.invalidate.subscribe((ids) => emitted.push(ids));

    const checkboxes = await loader.getAllHarnesses(MatCheckboxHarness);
    await checkboxes[0].check();
    await checkboxes[0].uncheck();
    fixture.detectChanges();

    expect(invalidateButton().disabled).toBe(true);
    forms()[1].dispatchEvent(new Event('submit'));
    expect(emitted).toEqual([]);
  });

  it('shows skeleton rows and disables actions while loading', () => {
    fixture.componentRef.setInput('rows', []);
    fixture.componentRef.setInput('loading', true);
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelectorAll('tbody tr').length).toBeGreaterThan(0);
    expect(fixture.nativeElement.querySelectorAll('.skeleton-bar').length).toBeGreaterThan(0);
    expect(fixture.nativeElement.querySelector('mat-checkbox')).toBeNull();
    expect(invalidateButton().disabled).toBe(true);
    expect((fixture.nativeElement.querySelector('.scrape-form button') as HTMLButtonElement).disabled).toBe(true);
  });

  it('emits scrape when the scrape form is submitted', () => {
    let scraped = 0;
    component.scrape.subscribe(() => scraped++);

    forms()[0].dispatchEvent(new Event('submit'));

    expect(scraped).toBe(1);
  });
});
