import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { RouterOutlet } from '@angular/router';
import { AuthStore } from './core/auth-store';
import { ListSelectionStore } from './core/list-selection-store';
import { Navbar } from './shared/navbar/navbar';

@Component({
  selector: 'app-root',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [RouterOutlet, Navbar],
  template: `
    <app-navbar
      [currentList]="listStore.current()"
      [username]="auth.username()"
      [isAdmin]="auth.isAdmin()"
      (logout)="auth.logout()" />
    <main class="app-container">
      <router-outlet />
    </main>
  `,
})
export class App {
  protected readonly listStore = inject(ListSelectionStore);
  protected readonly auth = inject(AuthStore);

  constructor() {
    this.listStore.load();
    this.auth.load();
  }
}
