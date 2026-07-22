import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatCheckboxModule } from '@angular/material/checkbox';
import { MatTableModule } from '@angular/material/table';
import { AdminUsersApi } from '../../core/api/admin-users-api';
import { AdminUser, CreateUserRequest, UpdateUserRequest } from '../../core/models';
import { ErrorAlert } from '../../shared/error-alert/error-alert';
import { Loading } from '../../shared/loading/loading';
import { UserCreateForm } from '../../shared/user-create-form/user-create-form';

/** Container: user administration (list, create, toggle roles/enabled, reset password, delete). */
@Component({
  selector: 'app-admin-users-page',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [MatTableModule, MatCheckboxModule, MatButtonModule, Loading, ErrorAlert, UserCreateForm],
  template: `
    <h1>User administration</h1>
    <app-error-alert [message]="error()" />

    @if (loading()) {
      <app-loading />
    } @else {
      <table mat-table [dataSource]="users()" [trackBy]="trackById">
        <ng-container matColumnDef="username">
          <th mat-header-cell *matHeaderCellDef>Username</th>
          <td mat-cell *matCellDef="let u">{{ u.username }}</td>
        </ng-container>
        <ng-container matColumnDef="email">
          <th mat-header-cell *matHeaderCellDef>E-mail</th>
          <td mat-cell *matCellDef="let u">{{ u.email }}</td>
        </ng-container>
        <ng-container matColumnDef="provider">
          <th mat-header-cell *matHeaderCellDef>Provider</th>
          <td mat-cell *matCellDef="let u">{{ u.provider }}</td>
        </ng-container>
        <ng-container matColumnDef="enabled">
          <th mat-header-cell *matHeaderCellDef>Enabled</th>
          <td mat-cell *matCellDef="let u">
            <mat-checkbox [checked]="u.enabled" (change)="toggleEnabled(u)" [aria-label]="'enabled ' + u.username" />
          </td>
        </ng-container>
        <ng-container matColumnDef="admin">
          <th mat-header-cell *matHeaderCellDef>Admin</th>
          <td mat-cell *matCellDef="let u">
            <mat-checkbox [checked]="isAdmin(u)" (change)="toggleAdmin(u)" [aria-label]="'admin ' + u.username" />
          </td>
        </ng-container>
        <ng-container matColumnDef="actions">
          <th mat-header-cell *matHeaderCellDef></th>
          <td mat-cell *matCellDef="let u">
            <button matButton (click)="resetPassword(u)" [disabled]="u.provider !== 'LOCAL'">Reset password</button>
            <button matButton="outlined" class="delete-button" (click)="remove(u)">Delete</button>
          </td>
        </ng-container>

        <tr mat-header-row *matHeaderRowDef="displayedColumns"></tr>
        <tr mat-row *matRowDef="let row; columns: displayedColumns"></tr>
      </table>

      <h2>Create user</h2>
      <app-user-create-form (create)="create($event)" />
    }
  `,
})
export class AdminUsersPage {
  private readonly api = inject(AdminUsersApi);

  protected readonly users = signal<AdminUser[]>([]);
  protected readonly loading = signal(true);
  protected readonly error = signal<string | null>(null);
  protected readonly displayedColumns = ['username', 'email', 'provider', 'enabled', 'admin', 'actions'];
  protected readonly trackById = (_: number, u: AdminUser) => u.id;

  constructor() {
    this.reload();
  }

  protected isAdmin(user: AdminUser): boolean {
    return user.roles.includes('ADMIN');
  }

  private reload(): void {
    this.loading.set(true);
    this.api.list().subscribe({
      next: (users) => {
        this.users.set(users);
        this.loading.set(false);
      },
      error: () => {
        this.error.set('Failed to load users.');
        this.loading.set(false);
      },
    });
  }

  protected toggleEnabled(user: AdminUser): void {
    this.applyUpdate(user, { email: user.email, roles: user.roles, enabled: !user.enabled });
  }

  protected toggleAdmin(user: AdminUser): void {
    const roles = this.isAdmin(user)
      ? user.roles.filter((r) => r !== 'ADMIN')
      : [...new Set([...user.roles, 'ADMIN'])];
    this.applyUpdate(user, { email: user.email, roles, enabled: user.enabled });
  }

  private applyUpdate(user: AdminUser, request: UpdateUserRequest): void {
    this.error.set(null);
    this.api.update(user.id, request).subscribe({
      next: () => this.reload(),
      error: (err) => this.showError(err, 'Update failed.'),
    });
  }

  protected create(request: CreateUserRequest): void {
    this.error.set(null);
    this.api.create(request).subscribe({
      next: () => this.reload(),
      error: (err) => this.showError(err, 'Could not create user.'),
    });
  }

  protected resetPassword(user: AdminUser): void {
    const password = window.prompt(`New password for ${user.username}:`);
    if (!password) {
      return;
    }
    this.api.resetPassword(user.id, password).subscribe({
      next: () => this.error.set(null),
      error: (err) => this.showError(err, 'Password reset failed.'),
    });
  }

  protected remove(user: AdminUser): void {
    if (!window.confirm(`Delete ${user.username}?`)) {
      return;
    }
    this.api.delete(user.id).subscribe({
      next: () => this.reload(),
      error: (err) => this.showError(err, 'Delete failed.'),
    });
  }

  private showError(err: unknown, fallback: string): void {
    const detail = (err as { error?: { detail?: string } })?.error?.detail;
    this.error.set(detail ?? fallback);
  }
}
