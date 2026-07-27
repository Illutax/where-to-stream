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
    fixture.detectChanges();

    const text = fixture.nativeElement.textContent;
    for (const label of ['Disney+', 'Amazon Prime', 'Youtube Store', 'Netflix', 'Sky WOW']) {
      expect(text).toContain(label);
    }
  });

  it('shows the watchlist size once known', () => {
    fixture.componentRef.setInput('watchlistCount', 42);
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('42');
  });

  it('omits the watchlist size until it is known', () => {
    fixture.componentRef.setInput('watchlistCount', null);
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).not.toContain('My list:');
  });

  it('shows the My Watchlist link only to authenticated users', () => {
    fixture.componentRef.setInput('username', null);
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).not.toContain('My Watchlist');

    fixture.componentRef.setInput('username', 'alice');
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('My Watchlist');
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

  it('hides the theme selector for an anonymous navbar', () => {
    fixture.componentRef.setInput('username', null);
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('mat-button-toggle-group')).toBeNull();
  });

  it('emits the picked theme when a theme toggle is clicked', () => {
    fixture.componentRef.setInput('username', 'alice');
    fixture.componentRef.setInput('theme', 'DARK');
    fixture.detectChanges();

    let picked: string | undefined;
    fixture.componentInstance.themeChange.subscribe((t) => (picked = t));

    const lightButton = fixture.nativeElement.querySelector(
      'button[aria-label="Light theme"]',
    ) as HTMLButtonElement;
    lightButton.click();
    fixture.detectChanges();

    expect(picked).toBe('LIGHT');
  });

  it('emits the age-rating preference when the toggle is switched off', () => {
    fixture.componentRef.setInput('username', 'alice');
    fixture.componentRef.setInput('showAgeRatings', true);
    fixture.detectChanges();

    let picked: boolean | undefined;
    fixture.componentInstance.showAgeRatingsChange.subscribe((v) => (picked = v));

    const toggle = fixture.nativeElement.querySelector('.app-nav__age-toggle button') as HTMLButtonElement;
    toggle.click();
    fixture.detectChanges();

    expect(picked).toBe(false);
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
