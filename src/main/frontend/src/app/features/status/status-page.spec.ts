import { provideHttpClient, withFetch } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { StatusPage } from './status-page';

describe('StatusPage', () => {
  let fixture: ComponentFixture<StatusPage>;
  let httpMock: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [StatusPage],
      providers: [provideHttpClient(withFetch()), provideHttpClientTesting()],
    });
    fixture = TestBed.createComponent(StatusPage);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpMock.verify());

  it('loads the status and renders the card with version and start time', () => {
    httpMock
      .expectOne((r) => r.url.endsWith('/api/status'))
      .flush({ version: '1.2.3', serverStart: '2026-01-01T00:00:00Z' });
    fixture.detectChanges();

    const text = fixture.nativeElement.textContent;
    expect(fixture.nativeElement.querySelector('app-status-card')).not.toBeNull();
    expect(text).toContain('1.2.3');
    expect(text).toContain('2026-01-01');
  });

  it('shows an error alert when status loading fails', () => {
    httpMock
      .expectOne((r) => r.url.endsWith('/api/status'))
      .flush('boom', { status: 500, statusText: 'Server Error' });
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('app-error-alert')).not.toBeNull();
  });
});
