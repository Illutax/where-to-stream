import { provideHttpClient, withFetch } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { UserPrefsStore } from '../../core/user-prefs-store';
import { translocoTesting } from '../../testing/transloco-testing';
import { ViewToggleButton } from './view-toggle-button';

describe('ViewToggleButton', () => {
  let fixture: ComponentFixture<ViewToggleButton>;
  let httpMock: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [ViewToggleButton, translocoTesting()],
      providers: [provideHttpClient(withFetch()), provideHttpClientTesting()],
    });
    fixture = TestBed.createComponent(ViewToggleButton);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpMock.verify());

  const button = () => fixture.nativeElement.querySelector('button') as HTMLButtonElement;

  it('offers to switch to the list view while in the (default) grid view', () => {
    fixture.detectChanges();

    expect(button().getAttribute('aria-label')).toBe('Switch to list view');
  });

  it('toggles the store and persists the change when clicked', () => {
    fixture.detectChanges();

    button().click();
    fixture.detectChanges();

    expect(TestBed.inject(UserPrefsStore).viewMode()).toBe('LIST');
    expect(button().getAttribute('aria-label')).toBe('Switch to tile view');
    httpMock.expectOne((r) => r.url.endsWith('/api/me/view-mode')).flush(null);

    button().click();
    fixture.detectChanges();

    expect(TestBed.inject(UserPrefsStore).viewMode()).toBe('GRID');
    httpMock.expectOne((r) => r.url.endsWith('/api/me/view-mode')).flush(null);
  });
});
