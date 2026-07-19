import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { Navbar } from './navbar';

describe('Navbar', () => {
  let fixture: ComponentFixture<Navbar>;

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [Navbar],
      providers: [provideRouter([])],
    });
    fixture = TestBed.createComponent(Navbar);
  });

  it('renders a link for every streaming provider', () => {
    fixture.componentRef.setInput('currentList', 'my-list.csv');
    fixture.detectChanges();

    const text = fixture.nativeElement.textContent;
    for (const label of ['Disney+', 'Amazon Prime', 'Google Play', 'Netflix', 'Sky WOW']) {
      expect(text).toContain(label);
    }
  });

  it('shows the current list name', () => {
    fixture.componentRef.setInput('currentList', 'my-list.csv');
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('my-list.csv');
  });

  it('shows a placeholder until the current list is known', () => {
    fixture.componentRef.setInput('currentList', null);
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('…');
  });
});
