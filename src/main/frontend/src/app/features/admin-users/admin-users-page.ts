import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatCheckboxModule } from '@angular/material/checkbox';
import { MatTableModule } from '@angular/material/table';
import { TranslocoPipe, TranslocoService } from '@jsverse/transloco';
import { AdminUsersApi } from '../../core/api/admin-users-api';
import { AdminUser, CreateUserRequest, UpdateUserRequest } from '../../core/models';
import { ErrorAlert } from '../../shared/error-alert/error-alert';
import { UserCreateForm } from '../../shared/user-create-form/user-create-form';

/** Placeholder rows shown while loading (see {@link AdminUsersPage.loading}) — never rendered as real data. */
const SKELETON_USERS: AdminUser[] = Array.from({ length: 5 }, (_, i) => ({
  id: `skeleton-${i}`,
  username: '',
  email: '',
  enabled: false,
  roles: [],
  provider: '',
}));

/** Container: user administration (list, create, toggle roles/enabled, reset password, delete). */
@Component({
  selector: 'app-admin-users-page',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [MatTableModule, MatCheckboxModule, MatButtonModule, ErrorAlert, UserCreateForm, TranslocoPipe],
  template: `
    <h1>{{ 'users.title' | transloco }}</h1>
    <app-error-alert [message]="error()" />

    <div class="table-scroll">
    <table mat-table [dataSource]="displayUsers()" [trackBy]="trackById">
      <ng-container matColumnDef="username">
        <th mat-header-cell *matHeaderCellDef>{{ 'users.columnUsername' | transloco }}</th>
        <td mat-cell *matCellDef="let u">
          @if (loading()) { <span class="skeleton-bar"></span> } @else { {{ u.username }} }
        </td>
      </ng-container>
      <ng-container matColumnDef="email">
        <th mat-header-cell *matHeaderCellDef>{{ 'users.columnEmail' | transloco }}</th>
        <td mat-cell *matCellDef="let u">
          @if (loading()) { <span class="skeleton-bar"></span> } @else { {{ u.email }} }
        </td>
      </ng-container>
      <ng-container matColumnDef="provider">
        <th mat-header-cell *matHeaderCellDef>{{ 'users.columnProvider' | transloco }}</th>
        <td mat-cell *matCellDef="let u">
          @if (loading()) { <span class="skeleton-bar skeleton-bar--narrow"></span> } @else { {{ u.provider }} }
        </td>
      </ng-container>
      <ng-container matColumnDef="enabled">
        <th mat-header-cell *matHeaderCellDef>{{ 'users.columnEnabled' | transloco }}</th>
        <td mat-cell *matCellDef="let u">
          @if (loading()) {
            <span class="skeleton-bar skeleton-rated"></span>
          } @else {
            <mat-checkbox [checked]="u.enabled" (change)="toggleEnabled(u)" [aria-label]="'users.enabledLabel' | transloco: { username: u.username }" />
          }
        </td>
      </ng-container>
      <ng-container matColumnDef="admin">
        <th mat-header-cell *matHeaderCellDef>{{ 'users.columnAdmin' | transloco }}</th>
        <td mat-cell *matCellDef="let u">
          @if (loading()) {
            <span class="skeleton-bar skeleton-rated"></span>
          } @else {
            <mat-checkbox [checked]="isAdmin(u)" (change)="toggleAdmin(u)" [aria-label]="'users.adminLabel' | transloco: { username: u.username }" />
          }
        </td>
      </ng-container>
      <ng-container matColumnDef="actions">
        <th mat-header-cell *matHeaderCellDef></th>
        <td mat-cell *matCellDef="let u">
          @if (!loading()) {
            <button matButton (click)="resetPassword(u)" [disabled]="u.provider !== 'LOCAL'">{{ 'users.resetPassword' | transloco }}</button>
            <button matButton="outlined" class="delete-button" (click)="remove(u)">{{ 'users.delete' | transloco }}</button>
          }
        </td>
      </ng-container>

      <tr mat-header-row *matHeaderRowDef="displayedColumns"></tr>
      <tr mat-row *matRowDef="let row; columns: displayedColumns"></tr>
    </table>
    </div>

    <h2>{{ 'users.createHeading' | transloco }}</h2>
    <app-user-create-form (create)="create($event)" />
  `,
})
export class AdminUsersPage {
  private readonly api = inject(AdminUsersApi);
  private readonly transloco = inject(TranslocoService);

  protected readonly users = signal<AdminUser[]>([]);
  protected readonly loading = signal(true);
  protected readonly error = signal<string | null>(null);
  protected readonly displayedColumns = ['username', 'email', 'provider', 'enabled', 'admin', 'actions'];
  protected readonly trackById = (_: number, u: AdminUser) => u.id;
  protected readonly displayUsers = computed(() => (this.loading() ? SKELETON_USERS : this.users()));

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
        this.error.set(this.transloco.translate('users.loadFailed'));
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
      error: (err) => this.showError(err, this.transloco.translate('users.updateFailed')),
    });
  }

  protected create(request: CreateUserRequest): void {
    this.error.set(null);
    this.api.create(request).subscribe({
      next: () => this.reload(),
      error: (err) => this.showError(err, this.transloco.translate('users.createFailed')),
    });
  }

  protected resetPassword(user: AdminUser): void {
    const password = window.prompt(this.transloco.translate('users.newPasswordPrompt', { username: user.username }));
    if (!password) {
      return;
    }
    this.api.resetPassword(user.id, password).subscribe({
      next: () => this.error.set(null),
      error: (err) => this.showError(err, this.transloco.translate('users.resetFailed')),
    });
  }

  protected remove(user: AdminUser): void {
    if (!window.confirm(this.transloco.translate('users.confirmDelete', { username: user.username }))) {
      return;
    }
    this.api.delete(user.id).subscribe({
      next: () => this.reload(),
      error: (err) => this.showError(err, this.transloco.translate('users.deleteFailed')),
    });
  }

  private showError(err: unknown, fallback: string): void {
    const detail = (err as { error?: { detail?: string } })?.error?.detail;
    this.error.set(detail ?? fallback);
  }
}
