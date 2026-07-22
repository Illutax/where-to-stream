import { provideHttpClient, withFetch } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideRouter, UrlTree } from '@angular/router';
import { Observable } from 'rxjs';
import { adminGuard } from './admin-guard';

describe('adminGuard', () => {
  let httpMock: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(withFetch()), provideHttpClientTesting(), provideRouter([])],
    });
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpMock.verify());

  function run(): Observable<boolean | UrlTree> {
    // eslint-disable-next-line @typescript-eslint/no-explicit-any
    return TestBed.runInInjectionContext(() => adminGuard({} as any, {} as any)) as Observable<boolean | UrlTree>;
  }

  it('allows admins', () => {
    let result: boolean | UrlTree | undefined;
    run().subscribe((r) => (result = r));
    httpMock.expectOne((r) => r.url.endsWith('/api/me'))
      .flush({ authenticated: true, username: 'a', roles: ['ADMIN'], admin: true });
    expect(result).toBe(true);
  });

  it('redirects non-admins to home', () => {
    let result: boolean | UrlTree | undefined;
    run().subscribe((r) => (result = r));
    httpMock.expectOne((r) => r.url.endsWith('/api/me'))
      .flush({ authenticated: true, username: 'u', roles: ['USER'], admin: false });
    expect(result).toBeInstanceOf(UrlTree);
  });
});
