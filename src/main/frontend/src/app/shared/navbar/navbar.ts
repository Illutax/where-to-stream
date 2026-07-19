import { ChangeDetectionStrategy, Component, input } from '@angular/core';
import { MatToolbarModule } from '@angular/material/toolbar';
import { MatButtonModule } from '@angular/material/button';
import { RouterLink, RouterLinkActive } from '@angular/router';
import { PROVIDERS } from '../../core/models';

/**
 * Presentational top navigation (Material toolbar). Renders the provider links and the
 * currently selected list; holds no data-loading logic — the active list name is passed in.
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
      <a matButton routerLink="/list" routerLinkActive="active-link">Change List</a>
      <a matButton routerLink="/manage" routerLinkActive="active-link">Manage Cache</a>
      <a matButton routerLink="/status" routerLinkActive="active-link">Status</a>
      <span class="app-toolbar__list">Selected list: {{ currentList() ?? '…' }}</span>
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
  protected readonly providers = PROVIDERS;
}
