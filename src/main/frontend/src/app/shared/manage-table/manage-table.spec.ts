import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ManageTable } from './manage-table';
import { ManageRow } from '../../core/models';

describe('ManageTable', () => {
  let fixture: ComponentFixture<ManageTable>;
  let component: ManageTable;

  const rows: ManageRow[] = [
    { imdbId: 'tt1', name: 'Alpha', isRated: true, needsScrape: true },
    { imdbId: 'tt2', name: 'Beta', isRated: false, needsScrape: false },
  ];

  beforeEach(() => {
    TestBed.configureTestingModule({ imports: [ManageTable] });
    fixture = TestBed.createComponent(ManageTable);
    component = fixture.componentInstance;
    fixture.componentRef.setInput('rows', rows);
    fixture.componentRef.setInput('needsScrapeCount', 1);
    fixture.detectChanges();
  });

  const checkboxes = () =>
    Array.from(fixture.nativeElement.querySelectorAll('input[type=checkbox]')) as HTMLInputElement[];
  const forms = () => Array.from(fixture.nativeElement.querySelectorAll('form')) as HTMLFormElement[];
  const invalidateButton = () =>
    fixture.nativeElement.querySelector('.btn-danger') as HTMLButtonElement;

  it('renders a checkbox per row and disables "Invalidate" until something is selected', () => {
    expect(checkboxes()).toHaveLength(2);
    expect(invalidateButton().disabled).toBe(true);
  });

  it('enables "Invalidate" once a row is selected', () => {
    checkboxes()[0].dispatchEvent(new Event('change'));
    fixture.detectChanges();
    expect(invalidateButton().disabled).toBe(false);
  });

  it('emits the selected ids on invalidate and then clears the selection', () => {
    const emitted: string[][] = [];
    component.invalidate.subscribe((ids) => emitted.push(ids));

    checkboxes()[0].dispatchEvent(new Event('change')); // select tt1
    checkboxes()[1].dispatchEvent(new Event('change')); // select tt2
    fixture.detectChanges();

    forms()[1].dispatchEvent(new Event('submit')); // the invalidate form

    expect(emitted).toEqual([['tt1', 'tt2']]);
    // selection cleared -> button disabled again
    fixture.detectChanges();
    expect(invalidateButton().disabled).toBe(true);
  });

  it('toggling a row off removes it from the selection', () => {
    const emitted: string[][] = [];
    component.invalidate.subscribe((ids) => emitted.push(ids));

    checkboxes()[0].dispatchEvent(new Event('change')); // select tt1
    checkboxes()[0].dispatchEvent(new Event('change')); // deselect tt1
    fixture.detectChanges();

    expect(invalidateButton().disabled).toBe(true);
    forms()[1].dispatchEvent(new Event('submit'));
    expect(emitted).toEqual([]); // nothing selected -> no emit
  });

  it('emits scrape when the scrape form is submitted', () => {
    let scraped = 0;
    component.scrape.subscribe(() => scraped++);

    forms()[0].dispatchEvent(new Event('submit')); // the scrape form

    expect(scraped).toBe(1);
  });
});
