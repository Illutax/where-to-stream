import { ChangeDetectionStrategy, Component, input, output } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatDividerModule } from '@angular/material/divider';
import { MatListModule } from '@angular/material/list';
import { RouterLink, RouterLinkActive } from '@angular/router';
import { PROVIDERS } from '../../core/models';

/**
 * Presentational navigation drawer (Material nav-list). Renders the provider links, the current
 * user's watchlist size, admin-only links, a Settings link, and emits a logout request. Emits
 * {@code navigate} on every link tap so the shell can close the drawer on small screens. Holds no
 * data-loading logic.
 */
@Component({
  selector: 'app-navbar',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [MatListModule, MatButtonModule, MatDividerModule, RouterLink, RouterLinkActive],
  template: `
    <mat-nav-list class="app-nav">
      <a mat-list-item routerLink="/" [routerLinkActiveOptions]="{ exact: true }"
         routerLinkActive="active-link" (click)="navigate.emit()">Home</a>
      @for (p of providers; track p.key) {
        <a mat-list-item [routerLink]="['/provider', p.key]" routerLinkActive="active-link"
           (click)="navigate.emit()">{{ p.label }}</a>
      }
      <a mat-list-item routerLink="/status" routerLinkActive="active-link"
         (click)="navigate.emit()">Status</a>

      @if (username()) {
        <a mat-list-item routerLink="/watchlist" routerLinkActive="active-link"
           (click)="navigate.emit()">My Watchlist</a>
        <a mat-list-item routerLink="/settings" routerLinkActive="active-link"
           (click)="navigate.emit()">Settings</a>
      }

      @if (isAdmin()) {
        <mat-divider />
        <a mat-list-item routerLink="/manage" routerLinkActive="active-link"
           (click)="navigate.emit()">Manage Cache</a>
        <a mat-list-item routerLink="/admin/users" routerLinkActive="active-link"
           (click)="navigate.emit()">Users</a>
      }

      <mat-divider />
      <div class="app-nav__meta">
        @if (watchlistCount() !== null) {
          <div>My list: {{ watchlistCount() }} titles</div>
        }
        @if (username()) {
          <div>Signed in as {{ username() }}</div>
          <button matButton="outlined" (click)="logout.emit()">Logout</button>
        }
      </div>
    </mat-nav-list>
  `,
})
export class Navbar {
  readonly watchlistCount = input<number | null>(null);
  readonly username = input<string | null>(null);
  readonly isAdmin = input<boolean>(false);
  readonly logout = output<void>();
  readonly navigate = output<void>();
  protected readonly providers = PROVIDERS;
}
