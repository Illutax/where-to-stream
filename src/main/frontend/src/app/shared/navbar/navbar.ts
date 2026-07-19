import { ChangeDetectionStrategy, Component, input } from '@angular/core';
import { RouterLink, RouterLinkActive } from '@angular/router';
import { PROVIDERS } from '../../core/models';

/**
 * Presentational top navigation. Renders the provider links and the currently selected list;
 * holds no data-loading logic — the active list name is passed in.
 */
@Component({
  selector: 'app-navbar',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [RouterLink, RouterLinkActive],
  template: `
    <nav class="navbar navbar-expand-lg navbar-dark bg-primary">
      <div class="container">
        <a class="navbar-brand" routerLink="/" [routerLinkActiveOptions]="{ exact: true }"
           routerLinkActive="fw-bold">W2S</a>
        <ul class="navbar-nav me-auto">
          @for (p of providers; track p.key) {
            <li class="nav-item mx-lg-2">
              <a class="nav-link text-light" [routerLink]="['/provider', p.key]"
                 routerLinkActive="fw-bold">{{ p.label }}</a>
            </li>
          }
        </ul>
        <ul class="navbar-nav">
          <li class="nav-item mx-lg-2">
            <a class="nav-link text-light" routerLink="/list" routerLinkActive="fw-bold">Change List</a>
          </li>
          <li class="nav-item mx-lg-2">
            <a class="nav-link text-light" routerLink="/manage" routerLinkActive="fw-bold">Manage Cache</a>
          </li>
          <li class="nav-item mx-lg-2">
            <a class="nav-link text-light" routerLink="/status" routerLinkActive="fw-bold">Status</a>
          </li>
          <li class="nav-item mx-lg-2 nav-link text-light">
            Selected list: <span>{{ currentList() ?? '…' }}</span>
          </li>
        </ul>
      </div>
    </nav>
  `,
})
export class Navbar {
  readonly currentList = input<string | null>(null);
  protected readonly providers = PROVIDERS;
}
