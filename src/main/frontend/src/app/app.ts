import { BreakpointObserver } from '@angular/cdk/layout';
import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { MatButtonModule } from '@angular/material/button';
import { MatSidenavModule } from '@angular/material/sidenav';
import { MatToolbarModule } from '@angular/material/toolbar';
import { RouterLink, RouterOutlet } from '@angular/router';
import { map } from 'rxjs';
import { AuthStore } from './core/auth-store';
import { ListSelectionStore } from './core/list-selection-store';
import { Navbar } from './shared/navbar/navbar';

@Component({
  selector: 'app-root',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [RouterOutlet, RouterLink, Navbar, MatToolbarModule, MatButtonModule, MatSidenavModule],
  template: `
    <mat-toolbar class="app-toolbar">
      <button matIconButton (click)="drawer.toggle()" aria-label="Toggle navigation">☰</button>
      <a class="app-brand" routerLink="/">W2S</a>
    </mat-toolbar>

    <mat-sidenav-container class="app-sidenav-container">
      <mat-sidenav
        #drawer
        class="app-sidenav"
        [mode]="handset() ? 'over' : 'side'"
        [opened]="!handset()">
        <app-navbar
          [currentList]="listStore.current()"
          [username]="auth.username()"
          [isAdmin]="auth.isAdmin()"
          (logout)="auth.logout()"
          (navigate)="handset() && drawer.close()" />
      </mat-sidenav>
      <mat-sidenav-content>
        <main class="app-container">
          <router-outlet />
        </main>
      </mat-sidenav-content>
    </mat-sidenav-container>
  `,
})
export class App {
  private readonly breakpoints = inject(BreakpointObserver);
  protected readonly listStore = inject(ListSelectionStore);
  protected readonly auth = inject(AuthStore);

  /** True on phone/narrow viewports: the drawer overlays and closes after navigation. */
  protected readonly handset = toSignal(
    this.breakpoints.observe('(max-width: 959.98px)').pipe(map((state) => state.matches)),
    { initialValue: false },
  );

  constructor() {
    this.listStore.load();
    this.auth.load();
  }
}
