import { HarnessLoader } from '@angular/cdk/testing';
import { TestbedHarnessEnvironment } from '@angular/cdk/testing/testbed';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MatCheckboxHarness } from '@angular/material/checkbox/testing';
import { MatSortHarness } from '@angular/material/sort/testing';
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
  const names = () =>
    Array.from(fixture.nativeElement.querySelectorAll('tbody tr')).map(
      (row) => (row as HTMLElement).querySelector('td:nth-child(2)')?.textContent?.trim(),
    );
  const rowCheckboxes = () => loader.getAllHarnesses(MatCheckboxHarness.with({ ancestor: 'tbody' }));
  const headerCheckbox = () => loader.getHarness(MatCheckboxHarness.with({ ancestor: 'thead' }));
  /** Row checkbox `<input>`s in table order (index 0 = first data row) — used for shift-click,
   * which needs a raw click with a modifier key rather than the harness's plain toggle(). */
  const rowCheckboxInputs = () =>
    Array.from(fixture.nativeElement.querySelectorAll('tbody input[type="checkbox"]')) as HTMLInputElement[];
  /**
   * Simulates a user click with an optional modifier key: `mousedown` (which ManageTable listens
   * to for the shift flag, see the component's doc comment on `onCheckboxMouseDown`) followed by
   * `click` (which triggers the checkbox's actual toggle) — matching real click event order.
   */
  const clickCheckbox = (input: HTMLInputElement, options: { shift?: boolean } = {}) => {
    const shiftKey = options.shift ?? false;
    input.dispatchEvent(new MouseEvent('mousedown', { bubbles: true, cancelable: true, shiftKey }));
    input.dispatchEvent(new MouseEvent('click', { bubbles: true, cancelable: true, shiftKey }));
  };

  it('renders a checkbox per row plus a "select all" header checkbox, and disables "Invalidate" until something is selected', async () => {
    expect(await rowCheckboxes()).toHaveLength(2);
    expect(await headerCheckbox()).toBeTruthy();
    expect(invalidateButton().disabled).toBe(true);
  });

  it('enables "Invalidate" once a row is selected', async () => {
    const checkboxes = await rowCheckboxes();
    await checkboxes[0].check();
    fixture.detectChanges();
    expect(invalidateButton().disabled).toBe(false);
  });

  it('emits the selected ids on invalidate and then clears the selection', async () => {
    const emitted: string[][] = [];
    component.invalidate.subscribe((ids) => emitted.push(ids));

    const checkboxes = await rowCheckboxes();
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

    const checkboxes = await rowCheckboxes();
    await checkboxes[0].check();
    await checkboxes[0].uncheck();
    fixture.detectChanges();

    expect(invalidateButton().disabled).toBe(true);
    forms()[1].dispatchEvent(new Event('submit'));
    expect(emitted).toEqual([]);
  });

  it('"select all" selects every row, and toggles them all off again', async () => {
    const master = await headerCheckbox();

    await master.check();
    fixture.detectChanges();
    expect(invalidateButton().disabled).toBe(false);
    expect(await (await rowCheckboxes())[0].isChecked()).toBe(true);
    expect(await (await rowCheckboxes())[1].isChecked()).toBe(true);

    await master.toggle(); // all rows are checked, so this un-checks everything again
    fixture.detectChanges();
    expect(invalidateButton().disabled).toBe(true);
  });

  it('shows "select all" as indeterminate when only some rows are selected', async () => {
    const master = await headerCheckbox();
    expect(await master.isIndeterminate()).toBe(false);

    await (await rowCheckboxes())[0].check();
    fixture.detectChanges();

    expect(await master.isIndeterminate()).toBe(true);
  });

  it('selects a contiguous range with shift+click, like other applications', () => {
    fixture.componentRef.setInput('rows', [
      { imdbId: imdbId('tt1'), name: 'Alpha', isRated: false, needsScrape: false, lastScrapedAt: null },
      { imdbId: imdbId('tt2'), name: 'Beta', isRated: false, needsScrape: false, lastScrapedAt: null },
      { imdbId: imdbId('tt3'), name: 'Gamma', isRated: false, needsScrape: false, lastScrapedAt: null },
    ]);
    fixture.detectChanges();

    const emitted: string[][] = [];
    component.invalidate.subscribe((ids) => emitted.push(ids));

    const [first, , third] = rowCheckboxInputs();
    clickCheckbox(first);
    fixture.detectChanges();
    clickCheckbox(third, { shift: true });
    fixture.detectChanges();

    forms()[1].dispatchEvent(new Event('submit'));
    expect(emitted[0]?.slice().sort()).toEqual(['tt1', 'tt2', 'tt3']);
  });

  it('a plain click after a shift-click range-select toggles only that single row, not the whole range again', () => {
    fixture.componentRef.setInput('rows', [
      { imdbId: imdbId('tt1'), name: 'Alpha', isRated: false, needsScrape: false, lastScrapedAt: null },
      { imdbId: imdbId('tt2'), name: 'Beta', isRated: false, needsScrape: false, lastScrapedAt: null },
      { imdbId: imdbId('tt3'), name: 'Gamma', isRated: false, needsScrape: false, lastScrapedAt: null },
    ]);
    fixture.detectChanges();

    const emitted: string[][] = [];
    component.invalidate.subscribe((ids) => emitted.push(ids));

    const [first, second, third] = rowCheckboxInputs();
    clickCheckbox(first); // plain click -> {tt1}
    fixture.detectChanges();
    clickCheckbox(third, { shift: true }); // range -> {tt1,tt2,tt3}
    fixture.detectChanges();
    clickCheckbox(second); // plain click -> toggles only tt2 off
    fixture.detectChanges();

    forms()[1].dispatchEvent(new Event('submit'));
    expect(emitted[0]?.slice().sort()).toEqual(['tt1', 'tt3']);
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

  it('sorts by title ascending then descending as the header is clicked', async () => {
    expect(names()).toEqual(['Alpha', 'Beta']); // input order, unsorted

    const sort = await TestbedHarnessEnvironment.loader(fixture).getHarness(MatSortHarness);
    const [titleHeader] = await sort.getSortHeaders({ label: 'Title' });

    await titleHeader.click();
    fixture.detectChanges();
    expect(names()).toEqual(['Alpha', 'Beta']);

    await titleHeader.click();
    fixture.detectChanges();
    expect(names()).toEqual(['Beta', 'Alpha']);
  });

  it('sorts by last-scraped date when the Status header is clicked, with "needs scrape" rows treated as the earliest timestamp', async () => {
    const sort = await TestbedHarnessEnvironment.loader(fixture).getHarness(MatSortHarness);
    const [statusHeader] = await sort.getSortHeaders({ label: 'Status' });

    await statusHeader.click();
    fixture.detectChanges();

    // tt1 (Alpha) needs scrape (never scraped) -> sorts first; tt2 (Beta) has a real lastScrapedAt.
    expect(names()).toEqual(['Alpha', 'Beta']);
  });
});
