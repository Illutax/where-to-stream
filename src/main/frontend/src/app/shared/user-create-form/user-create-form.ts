import { ChangeDetectionStrategy, Component, output, signal } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatCheckboxModule } from '@angular/material/checkbox';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { TranslocoPipe } from '@jsverse/transloco';
import { CreateUserRequest } from '../../core/models';

/** Presentational "create user" form. Emits a CreateUserRequest; performs no data loading. */
@Component({
  selector: 'app-user-create-form',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [MatFormFieldModule, MatInputModule, MatCheckboxModule, MatButtonModule, TranslocoPipe],
  template: `
    <form (submit)="onSubmit($event)" class="create-form">
      <mat-form-field appearance="outline">
        <mat-label>{{ 'userForm.username' | transloco }}</mat-label>
        <input matInput type="text" [value]="username()" (input)="username.set($any($event.target).value)" required />
      </mat-form-field>
      <mat-form-field appearance="outline">
        <mat-label>{{ 'userForm.password' | transloco }}</mat-label>
        <input matInput type="password" [value]="password()" (input)="password.set($any($event.target).value)" required />
      </mat-form-field>
      <mat-form-field appearance="outline">
        <mat-label>{{ 'userForm.email' | transloco }}</mat-label>
        <input matInput type="email" [value]="email()" (input)="email.set($any($event.target).value)" />
      </mat-form-field>
      <mat-checkbox [checked]="admin()" (change)="admin.set($any($event).checked)">{{ 'userForm.admin' | transloco }}</mat-checkbox>
      <button matButton="filled" type="submit" [disabled]="!username() || !password()">{{ 'userForm.create' | transloco }}</button>
    </form>
  `,
  styles: `
    .create-form {
      display: flex;
      flex-wrap: wrap;
      gap: 0.75rem;
      align-items: center;
    }
  `,
})
export class UserCreateForm {
  readonly create = output<CreateUserRequest>();

  protected readonly username = signal('');
  protected readonly password = signal('');
  protected readonly email = signal('');
  protected readonly admin = signal(false);

  protected onSubmit(event: Event): void {
    event.preventDefault();
    if (!this.username() || !this.password()) {
      return;
    }
    this.create.emit({
      username: this.username(),
      password: this.password(),
      email: this.email() || null,
      roles: this.admin() ? ['ADMIN', 'USER'] : ['USER'],
    });
    this.username.set('');
    this.password.set('');
    this.email.set('');
    this.admin.set(false);
  }
}
