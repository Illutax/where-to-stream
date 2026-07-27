import { provideHttpClient, withFetch } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { SettingsPage } from './settings-page';
import { translocoTesting } from '../../testing/transloco-testing';

describe('SettingsPage', () => {
  let fixture: ComponentFixture<SettingsPage>;
  let httpMock: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [SettingsPage, translocoTesting()],
      providers: [provideHttpClient(withFetch()), provideHttpClientTesting()],
    });
    fixture = TestBed.createComponent(SettingsPage);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpMock.verify());

  it('renders the settings sections', () => {
    fixture.detectChanges();

    const text = fixture.nativeElement.textContent as string;
    expect(text).toContain('Settings');
    expect(text).toContain('Language');
    expect(text).toContain('Appearance');
    expect(text).toContain('Account');
  });

  it('toggling the age-rating switch persists the preference', () => {
    fixture.detectChanges();

    // First slide-toggle is "Age ratings" (defaults on → click turns it off).
    const toggle = fixture.nativeElement.querySelectorAll('mat-slide-toggle button')[0] as HTMLButtonElement;
    toggle.click();
    fixture.detectChanges();

    const req = httpMock.expectOne((r) => r.url.endsWith('/api/me/show-age-ratings'));
    expect(req.request.body).toEqual({ showAgeRatings: false });
    req.flush(null);
  });
});
