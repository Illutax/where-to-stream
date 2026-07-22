import { HarnessLoader } from '@angular/cdk/testing';
import { TestbedHarnessEnvironment } from '@angular/cdk/testing/testbed';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MatCheckboxHarness } from '@angular/material/checkbox/testing';
import { UserCreateForm } from './user-create-form';
import { CreateUserRequest } from '../../core/models';

describe('UserCreateForm', () => {
  let fixture: ComponentFixture<UserCreateForm>;
  let component: UserCreateForm;
  let loader: HarnessLoader;

  beforeEach(() => {
    TestBed.configureTestingModule({ imports: [UserCreateForm] });
    fixture = TestBed.createComponent(UserCreateForm);
    component = fixture.componentInstance;
    fixture.detectChanges();
    loader = TestbedHarnessEnvironment.loader(fixture);
  });

  function setInput(type: string, value: string): void {
    const input = fixture.nativeElement.querySelector(`input[type=${type}]`) as HTMLInputElement;
    input.value = value;
    input.dispatchEvent(new Event('input'));
  }

  it('emits a USER-only request by default', () => {
    let emitted: CreateUserRequest | undefined;
    component.create.subscribe((r) => (emitted = r));

    setInput('text', 'bob');
    setInput('password', 'secret');
    fixture.nativeElement.querySelector('form').dispatchEvent(new Event('submit'));

    expect(emitted).toEqual({ username: 'bob', password: 'secret', email: null, roles: ['USER'] });
  });

  it('includes ADMIN when the admin box is checked', async () => {
    let emitted: CreateUserRequest | undefined;
    component.create.subscribe((r) => (emitted = r));

    setInput('text', 'root');
    setInput('password', 'pw');
    await (await loader.getHarness(MatCheckboxHarness)).check();
    fixture.nativeElement.querySelector('form').dispatchEvent(new Event('submit'));

    expect(emitted?.roles).toEqual(['ADMIN', 'USER']);
  });

  it('does not emit without a username and password', () => {
    let emitted: CreateUserRequest | undefined;
    component.create.subscribe((r) => (emitted = r));

    fixture.nativeElement.querySelector('form').dispatchEvent(new Event('submit'));

    expect(emitted).toBeUndefined();
  });
});
