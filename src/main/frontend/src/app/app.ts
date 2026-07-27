import { BreakpointObserver } from '@angular/cdk/layout';
import { ChangeDetectionStrategy, Component, effect, inject } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { MatButtonModule } from '@angular/material/button';
import { MatSidenavModule } from '@angular/material/sidenav';
import { MatToolbarModule } from '@angular/material/toolbar';
import { RouterLink, RouterOutlet } from '@angular/router';
import { map } from 'rxjs';
import { AgeRatingStore } from './core/age-rating-store';
import { AuthStore } from './core/auth-store';
import { ThemeStore } from './core/theme-store';
import { WatchlistStore } from './core/watchlist-store';
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
          [watchlistCount]="watchlistStore.count()"
          [username]="auth.username()"
          [isAdmin]="auth.isAdmin()"
          [theme]="themeStore.theme()"
          [showAgeRatings]="ageRatingStore.showAgeRatings()"
          (logout)="auth.logout()"
          (themeChange)="themeStore.set($event)"
          (showAgeRatingsChange)="ageRatingStore.set($event)"
          (navigate)="handset() && drawer.close()" />
      </mat-sidenav>
      <mat-sidenav-content>
        <main class="app-container">
          <router-outlet />
        </main>
        @if (auth.tmdbAttribution()) {
          <footer class="app-footer">
            <a href="https://www.themoviedb.org/" target="_blank" rel="noopener">
              <img class="tmdb-logo" src="tmdb.svg" alt="The Movie Database (TMDB)" />
            </a>
            <span>This product uses the TMDB API but is not endorsed or certified by TMDB.</span>
          </footer>
        }
      </mat-sidenav-content>
    </mat-sidenav-container>
  `,
})
export class App {
  private readonly breakpoints = inject(BreakpointObserver);
  protected readonly watchlistStore = inject(WatchlistStore);
  protected readonly auth = inject(AuthStore);
  protected readonly themeStore = inject(ThemeStore);
  protected readonly ageRatingStore = inject(AgeRatingStore);

  /** True on phone/narrow viewports: the drawer overlays and closes after navigation. */
  protected readonly handset = toSignal(
    this.breakpoints.observe('(max-width: 959.98px)').pipe(map((state) => state.matches)),
    { initialValue: false },
  );

  constructor() {
    this.watchlistStore.load();
    this.auth.load();
    // Adopt the theme once the principal (with its saved preference) has loaded.
    effect(() => {
      const me = this.auth.me();
      if (me) {
        this.themeStore.init(me.theme);
        this.ageRatingStore.init(me.showAgeRatings);
      }
    });
  }
}
