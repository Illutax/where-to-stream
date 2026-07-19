import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ListPicker } from './list-picker';

describe('ListPicker', () => {
  let fixture: ComponentFixture<ListPicker>;
  let component: ListPicker;

  beforeEach(() => {
    TestBed.configureTestingModule({ imports: [ListPicker] });
    fixture = TestBed.createComponent(ListPicker);
    component = fixture.componentInstance;
    fixture.componentRef.setInput('current', 'b.csv');
    fixture.componentRef.setInput('available', ['a.csv', 'b.csv', 'c.csv']);
    fixture.detectChanges();
  });

  const select = () => fixture.nativeElement.querySelector('select') as HTMLSelectElement;
  const form = () => fixture.nativeElement.querySelector('form') as HTMLFormElement;

  it('renders an option per available list', () => {
    expect(select().querySelectorAll('option')).toHaveLength(3);
  });

  it('emits the current list when submitted without changing the selection', () => {
    let chosen: string | undefined;
    component.change.subscribe((name) => (chosen = name));

    form().dispatchEvent(new Event('submit'));

    expect(chosen).toBe('b.csv');
  });

  it('emits the newly picked list', () => {
    let chosen: string | undefined;
    component.change.subscribe((name) => (chosen = name));

    select().value = 'c.csv';
    select().dispatchEvent(new Event('change'));
    form().dispatchEvent(new Event('submit'));

    expect(chosen).toBe('c.csv');
  });

  it('disables the submit button when [disabled] is set', () => {
    fixture.componentRef.setInput('disabled', true);
    fixture.detectChanges();
    expect((fixture.nativeElement.querySelector('button') as HTMLButtonElement).disabled).toBe(true);
  });
});
