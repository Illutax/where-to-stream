import { HttpErrorResponse } from '@angular/common/http';
import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatButtonToggleModule } from '@angular/material/button-toggle';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { MatSlideToggleModule } from '@angular/material/slide-toggle';
import { MatSnackBar } from '@angular/material/snack-bar';
import { AgeRatingStore } from '../../core/age-rating-store';
import { UsernameApi } from '../../core/api/username-api';
import { AuthStore } from '../../core/auth-store';
import { GermanTitleStore } from '../../core/german-title-store';
import { LanguageStore } from '../../core/language-store';
import { ThemeStore } from '../../core/theme-store';

/**
 * The user's settings: display language, colour theme, age-rating badges, German film titles, and
 * renaming the login username. Reads/writes the preference stores directly; renaming the username
 * ends the session, so it redirects to the login page.
 */
@Component({
  selector: 'app-settings-page',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    MatCardModule, MatFormFieldModule, MatInputModule, MatSelectModule,
    MatButtonModule, MatButtonToggleModule, MatSlideToggleModule,
  ],
  template: `
    <h1>Settings</h1>

    <mat-card class="settings-card">
      <h2>Language</h2>
      <mat-form-field appearance="outline">
        <mat-label>Language</mat-label>
        <mat-select [value]="languageStore.language()" (selectionChange)="languageStore.set($event.value)">
          <mat-option value="EN">English</mat-option>
          <mat-option value="DE">Deutsch</mat-option>
        </mat-select>
      </mat-form-field>
    </mat-card>

    <mat-card class="settings-card">
      <h2>Appearance</h2>
      <div class="setting-row">
        <span>Theme</span>
        <mat-button-toggle-group [value]="themeStore.theme()" (change)="themeStore.set($event.value)"
                                 hideSingleSelectionIndicator aria-label="Colour theme">
          <mat-button-toggle value="SYSTEM" aria-label="System theme" title="Follow system">🖥️</mat-button-toggle>
          <mat-button-toggle value="LIGHT" aria-label="Light theme" title="Light">☀️</mat-button-toggle>
          <mat-button-toggle value="DARK" aria-label="Dark theme" title="Dark">🌙</mat-button-toggle>
        </mat-button-toggle-group>
      </div>
      <mat-slide-toggle [checked]="ageRatingStore.showAgeRatings()"
                        (change)="ageRatingStore.set($event.checked)">Age ratings</mat-slide-toggle>
      <mat-slide-toggle [checked]="germanTitleStore.show()"
                        (change)="germanTitleStore.set($event.checked)">German titles</mat-slide-toggle>
    </mat-card>

    <mat-card class="settings-card">
      <h2>Account</h2>
      <p class="text-muted">Changing your username signs you out — you then log in with the new name.</p>
      <form (submit)="rename($event)" class="setting-row">
        <mat-form-field appearance="outline">
          <mat-label>Username</mat-label>
          <input matInput type="text" [value]="username()" (input)="username.set($any($event.target).value)" required />
        </mat-form-field>
        <button matButton="filled" type="submit" [disabled]="!canRename()">Change username</button>
      </form>
    </mat-card>
  `,
  styles: `
    .settings-card {
      max-width: 32rem;
      margin: 0 0 1rem;
      padding: 1rem;
      display: flex;
      flex-direction: column;
      gap: 0.75rem;
      align-items: flex-start;
    }
    .setting-row {
      display: flex;
      align-items: center;
      gap: 1rem;
      flex-wrap: wrap;
    }
    .settings-card h2 {
      margin: 0;
    }
  `,
})
export class SettingsPage {
  protected readonly themeStore = inject(ThemeStore);
  protected readonly ageRatingStore = inject(AgeRatingStore);
  protected readonly languageStore = inject(LanguageStore);
  protected readonly germanTitleStore = inject(GermanTitleStore);
  private readonly auth = inject(AuthStore);
  private readonly usernameApi = inject(UsernameApi);
  private readonly snackBar = inject(MatSnackBar);

  protected readonly username = signal(this.auth.username() ?? '');
  protected readonly canRename = computed(() => {
    const trimmed = this.username().trim();
    return trimmed.length > 0 && trimmed !== this.auth.username();
  });

  protected rename(event: Event): void {
    event.preventDefault();
    if (!this.canRename()) {
      return;
    }
    this.usernameApi.setUsername(this.username().trim()).subscribe({
      next: () => this.redirectToLogin(),
      error: (err: HttpErrorResponse) =>
        this.snackBar.open(
          err.status === 409 ? 'That username is already taken.' : 'Could not change the username.',
          'Dismiss',
          { duration: 5000 },
        ),
    });
  }

  private redirectToLogin(): void {
    window.location.href = new URL('../login', document.baseURI).toString();
  }
}
