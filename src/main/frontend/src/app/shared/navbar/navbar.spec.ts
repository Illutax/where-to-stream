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

  it('hides admin links and the logout for an anonymous/non-admin navbar', () => {
    fixture.componentRef.setInput('username', null);
    fixture.componentRef.setInput('isAdmin', false);
    fixture.detectChanges();

    const text = fixture.nativeElement.textContent;
    expect(text).not.toContain('Users');
    expect(text).not.toContain('Logout');
  });

  it('shows the username, logout and admin links for an admin', () => {
    fixture.componentRef.setInput('username', 'alice');
    fixture.componentRef.setInput('isAdmin', true);
    fixture.detectChanges();

    const text = fixture.nativeElement.textContent;
    expect(text).toContain('alice');
    expect(text).toContain('Users');
    expect(text).toContain('Manage Cache');
    expect(text).toContain('Logout');
  });

  it('emits logout when the logout button is clicked', () => {
    let loggedOut = false;
    fixture.componentRef.setInput('username', 'alice');
    fixture.componentInstance.logout.subscribe(() => (loggedOut = true));
    fixture.detectChanges();

    const buttons = Array.from(fixture.nativeElement.querySelectorAll('button')) as HTMLButtonElement[];
    buttons.find((b) => b.textContent?.includes('Logout'))?.click();

    expect(loggedOut).toBe(true);
  });
});
