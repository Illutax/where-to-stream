import { ChangeDetectionStrategy, Component, input, output } from '@angular/core';
import { MatToolbarModule } from '@angular/material/toolbar';
import { MatButtonModule } from '@angular/material/button';
import { RouterLink, RouterLinkActive } from '@angular/router';
import { PROVIDERS } from '../../core/models';

/**
 * Presentational top navigation (Material toolbar). Renders provider links, the current list and
 * user, admin-only links, and emits a logout request; holds no data-loading logic.
 */
@Component({
  selector: 'app-navbar',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [MatToolbarModule, MatButtonModule, RouterLink, RouterLinkActive],
  template: `
    <mat-toolbar class="app-toolbar">
      <a matButton routerLink="/" [routerLinkActiveOptions]="{ exact: true }"
         routerLinkActive="active-link">W2S</a>
      @for (p of providers; track p.key) {
        <a matButton [routerLink]="['/provider', p.key]" routerLinkActive="active-link">{{ p.label }}</a>
      }
      <span class="app-toolbar__spacer"></span>
      <a matButton routerLink="/status" routerLinkActive="active-link">Status</a>
      @if (isAdmin()) {
        <a matButton routerLink="/list" routerLinkActive="active-link">Change List</a>
        <a matButton routerLink="/manage" routerLinkActive="active-link">Manage Cache</a>
        <a matButton routerLink="/admin/users" routerLinkActive="active-link">Users</a>
      }
      <span class="app-toolbar__list">Selected list: {{ currentList() ?? '…' }}</span>
      @if (username()) {
        <span class="app-toolbar__list">{{ username() }}</span>
        <button matButton (click)="logout.emit()">Logout</button>
      }
    </mat-toolbar>
  `,
  styles: `
    .app-toolbar__list {
      padding: 0 0.75rem;
      font: var(--mat-sys-label-large);
    }
  `,
})
export class Navbar {
  readonly currentList = input<string | null>(null);
  readonly username = input<string | null>(null);
  readonly isAdmin = input<boolean>(false);
  readonly logout = output<void>();
  protected readonly providers = PROVIDERS;
}
