import { provideHttpClient, withFetch } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ImpersonationBanner } from './impersonation-banner';
import { AuthStore } from '../../core/auth-store';
import { Me } from '../../core/models';
import { translocoTesting } from '../../testing/transloco-testing';

function me(overrides: Partial<Me>): Me {
  return {
    authenticated: true, username: 'alice', roles: ['USER'], admin: false, theme: 'SYSTEM',
    tmdbAttribution: false, showAgeRatings: true, language: 'EN', showGermanTitle: false,
    viewMode: 'GRID', tilesPerRow: 6, ebayMarketplace: 'EBAY_DE', impersonatedBy: null,
    ...overrides,
  };
}

describe('ImpersonationBanner', () => {
  let fixture: ComponentFixture<ImpersonationBanner>;
  let httpMock: HttpTestingController;
  let authStore: AuthStore;

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [ImpersonationBanner, translocoTesting()],
      providers: [provideHttpClient(withFetch()), provideHttpClientTesting()],
    });
    fixture = TestBed.createComponent(ImpersonationBanner);
    httpMock = TestBed.inject(HttpTestingController);
    authStore = TestBed.inject(AuthStore);
  });

  afterEach(() => httpMock.verify());

  function load(overrides: Partial<Me>): void {
    authStore.load();
    httpMock.expectOne((r) => r.url.endsWith('/api/me')).flush(me(overrides));
    fixture.detectChanges();
  }

  it('renders nothing for an ordinary user', () => {
    load({ impersonatedBy: null });

    // An ordinary user must not learn that the feature exists at all — no banner, no hint.
    expect(fixture.nativeElement.textContent.trim()).toBe('');
    expect(fixture.nativeElement.querySelector('button')).toBeNull();
  });

  it('names both identities while an impersonation is running', () => {
    load({ username: 'bob', impersonatedBy: 'admin' });

    const text = fixture.nativeElement.textContent as string;
    // Both names, because the dangerous mistake is forgetting which session one is looking at.
    expect(text).toContain('bob');
    expect(text).toContain('admin');
  });

  it('offers the way back and calls the exit endpoint, not the admin one', () => {
    load({ username: 'bob', impersonatedBy: 'admin' });

    (fixture.nativeElement.querySelector('button') as HTMLButtonElement).click();

    // Not /api/admin/...: while switched the session has no admin role, so an admin-guarded exit
    // would be closed to exactly this caller.
    const req = httpMock.expectOne((r) => r.url.endsWith('/api/impersonate/exit'));
    expect(req.request.method).toBe('POST');
    req.flush(null);
  });
});
