import { BreakpointObserver } from '@angular/cdk/layout';
import { ChangeDetectionStrategy, Component, effect, inject } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { MatButtonModule } from '@angular/material/button';
import { MatSidenavModule } from '@angular/material/sidenav';
import { MatToolbarModule } from '@angular/material/toolbar';
import { RouterLink, RouterOutlet } from '@angular/router';
import { TranslocoPipe, TranslocoService } from '@jsverse/transloco';
import { map } from 'rxjs';
import { AgeRatingStore } from './core/age-rating-store';
import { AuthStore } from './core/auth-store';
import { GermanTitleStore } from './core/german-title-store';
import { GridPrefsStore } from './core/grid-prefs-store';
import { LanguageStore } from './core/language-store';
import { ThemeStore } from './core/theme-store';
import { WatchlistStore } from './core/watchlist-store';
import { ImdbSearchBox } from './shared/imdb-search-box/imdb-search-box';
import { Navbar } from './shared/navbar/navbar';

@Component({
  selector: 'app-root',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [RouterOutlet, RouterLink, Navbar, ImdbSearchBox, MatToolbarModule, MatButtonModule, MatSidenavModule, TranslocoPipe],
  template: `
    <mat-toolbar class="app-toolbar">
      <button matIconButton (click)="drawer.toggle()" [attr.aria-label]="'app.toggleNav' | transloco">☰</button>
      <a class="app-brand" routerLink="/">W2S</a>
      <span class="app-toolbar-spacer"></span>
      @if (auth.authenticated()) {
        <app-imdb-search-box />
      }
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
          (logout)="auth.logout()"
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
  private readonly languageStore = inject(LanguageStore);
  private readonly germanTitleStore = inject(GermanTitleStore);
  private readonly gridPrefsStore = inject(GridPrefsStore);
  private readonly transloco = inject(TranslocoService);

  /** True on phone/narrow viewports: the drawer overlays and closes after navigation. */
  protected readonly handset = toSignal(
    this.breakpoints.observe('(max-width: 959.98px)').pipe(map((state) => state.matches)),
    { initialValue: false },
  );

  constructor() {
    this.watchlistStore.load();
    this.auth.load();
    // Drive the active UI language off the user's preference (EN/DE -> en/de).
    effect(() => this.transloco.setActiveLang(this.languageStore.language().toLowerCase()));
    // Adopt the theme once the principal (with its saved preference) has loaded.
    effect(() => {
      const me = this.auth.me();
      if (me) {
        this.themeStore.init(me.theme);
        this.ageRatingStore.init(me.showAgeRatings);
        this.languageStore.init(me.language);
        this.germanTitleStore.init(me.showGermanTitle);
        this.gridPrefsStore.init(me.viewMode, me.tilesPerRow);
      }
    });
  }
}
