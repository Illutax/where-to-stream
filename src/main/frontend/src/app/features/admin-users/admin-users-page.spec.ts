import { provideHttpClient, withFetch } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { By } from '@angular/platform-browser';
import { AdminUsersPage } from './admin-users-page';
import { UserCreateForm } from '../../shared/user-create-form/user-create-form';
import { AdminUser } from '../../core/models';

describe('AdminUsersPage', () => {
  let fixture: ComponentFixture<AdminUsersPage>;
  let httpMock: HttpTestingController;

  const user = (over: Partial<AdminUser>): AdminUser => ({
    id: '1', username: 'admin', email: 'a@x', enabled: true, roles: ['ADMIN', 'USER'], provider: 'LOCAL', ...over,
  });

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [AdminUsersPage],
      providers: [provideHttpClient(withFetch()), provideHttpClientTesting()],
    });
    fixture = TestBed.createComponent(AdminUsersPage);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpMock.verify());

  function flushList(users: AdminUser[]): void {
    httpMock.expectOne((r) => r.url.endsWith('/api/admin/users') && r.method === 'GET').flush(users);
    fixture.detectChanges();
  }

  it('loads and renders the users', () => {
    flushList([user({ id: '1', username: 'admin' }), user({ id: '2', username: 'bob', roles: ['USER'] })]);
    expect(fixture.nativeElement.querySelectorAll('tbody tr')).toHaveLength(2);
    expect(fixture.nativeElement.textContent).toContain('bob');
  });

  it('creates a user via the child form and reloads', () => {
    flushList([]);
    const form = fixture.debugElement.query(By.directive(UserCreateForm)).componentInstance as UserCreateForm;

    form.create.emit({ username: 'bob', password: 'pw', email: null, roles: ['USER'] });

    const post = httpMock.expectOne((r) => r.url.endsWith('/api/admin/users') && r.method === 'POST');
    expect(post.request.body).toEqual({ username: 'bob', password: 'pw', email: null, roles: ['USER'] });
    post.flush(user({ id: '2', username: 'bob' }));
    httpMock.expectOne((r) => r.url.endsWith('/api/admin/users') && r.method === 'GET').flush([]);
  });

  it('shows an error when loading fails', () => {
    httpMock.expectOne((r) => r.url.endsWith('/api/admin/users'))
      .flush('boom', { status: 500, statusText: 'Server Error' });
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('app-error-alert')).not.toBeNull();
  });
});
