import { HarnessLoader, parallel } from '@angular/cdk/testing';
import { TestbedHarnessEnvironment } from '@angular/cdk/testing/testbed';
import { provideHttpClient, withFetch } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MatSelectHarness } from '@angular/material/select/testing';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { SettingsPage } from './settings-page';
import { translocoTesting } from '../../testing/transloco-testing';

/** The marketplace options in template order: the label the user picks, the value the server gets. */
const MARKETPLACES = [
  { label: 'ebay.de (euro)', value: 'EBAY_DE' },
  { label: 'ebay.com (US dollar)', value: 'EBAY_US' },
  { label: 'ebay.co.uk (pound)', value: 'EBAY_GB' },
];

/** Same three, rotated so no option is ever picked while it is the selected one (see the spec below). */
const PICK_ORDER = [MARKETPLACES[1], MARKETPLACES[2], MARKETPLACES[0]];

describe('SettingsPage', () => {
  let fixture: ComponentFixture<SettingsPage>;
  let httpMock: HttpTestingController;
  let loader: HarnessLoader;

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [SettingsPage, translocoTesting()],
      providers: [provideHttpClient(withFetch()), provideHttpClientTesting(), provideNoopAnimations()],
    });
    fixture = TestBed.createComponent(SettingsPage);
    httpMock = TestBed.inject(HttpTestingController);
    loader = TestbedHarnessEnvironment.loader(fixture);
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

  it('offers exactly the three marketplaces the server accepts', async () => {
    fixture.detectChanges();
    const select = await loader.getHarness(MatSelectHarness.with({ label: 'eBay marketplace' }));

    await select.open();
    const options = await select.getOptions();
    const labels = await parallel(() => options.map((option) => option.getText()));
    expect(labels).toEqual(MARKETPLACES.map(({ label }) => label));
    await select.close();

    // A fourth option here, or a renamed value, would be stored and then refused by the server's
    // EbayMarketplace check — a setting that appears to save and silently does not.
    // The harness sees what the user sees (the label), while it is the bound value the server has
    // to accept, so each option is picked and the value it persists is read off the PUT it fires.
    // Order matters: mat-select emits no selectionChange for the option that is already selected,
    // and EBAY_DE is the default — so it is picked last, after the value has moved away from it.
    const persisted: string[] = [];
    for (const { label } of PICK_ORDER) {
      await select.clickOptions({ text: label });
      const req = httpMock.expectOne((r) => r.url.endsWith('/api/me/ebay-marketplace'));
      persisted.push((req.request.body as { marketplace: string }).marketplace);
      req.flush(null);
    }
    expect(persisted).toEqual(PICK_ORDER.map(({ value }) => value));
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
