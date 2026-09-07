import { provideHttpClient, withFetch } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { By } from '@angular/platform-browser';
import { MatSelect } from '@angular/material/select';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { SettingsPage } from './settings-page';
import { translocoTesting } from '../../testing/transloco-testing';

describe('SettingsPage', () => {
  let fixture: ComponentFixture<SettingsPage>;
  let httpMock: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [SettingsPage, translocoTesting()],
      providers: [provideHttpClient(withFetch()), provideHttpClientTesting(), provideNoopAnimations()],
    });
    fixture = TestBed.createComponent(SettingsPage);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpMock.verify());

  it('renders the settings sections', () => {
    fixture.detectChanges();

    // One assertion so a renamed heading reports every section at once, not just the first
    // (ADR-0005). "eBay" replaced "Price lookup" when the lookup was withdrawn (TODO-56) — the
    // card now only picks the marketplace the search link opens.
    const text = fixture.nativeElement.textContent as string;
    expect(['Settings', 'Language', 'eBay', 'Appearance', 'Account'].filter((s) => !text.includes(s)))
      .toEqual([]);
  });

  it('offers exactly the three marketplaces the server accepts', () => {
    fixture.detectChanges();

    // mat-option lives in a lazily instantiated template, so the options only exist once the panel
    // is opened — querying the DOM beforehand finds nothing.
    const selects = fixture.debugElement.queryAll(By.directive(MatSelect));
    const values = selects.flatMap((select) => {
      const matSelect = select.componentInstance as MatSelect;
      matSelect.open();
      fixture.detectChanges();
      return matSelect.options.map((option) => option.value as string);
    });

    // A fourth option here, or a renamed value, would be stored and then refused by the server's
    // SupportedMarketplaces check — a setting that appears to save and silently does not.
    expect(values).toContain('EBAY_DE');
    expect(values).toContain('EBAY_US');
    expect(values).toContain('EBAY_GB');
    expect(values.filter((value) => value.startsWith('EBAY_'))).toHaveLength(3);
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
