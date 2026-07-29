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
import { TranslocoPipe, TranslocoService } from '@jsverse/transloco';
import { UsernameApi } from '../../core/api/username-api';
import { AuthStore } from '../../core/auth-store';
import { UserPrefsStore } from '../../core/user-prefs-store';

/**
 * The user's settings: display language, colour theme, age-rating badges, German film titles,
 * and renaming the login username.
 * Reads/writes the preference stores directly;
 * renaming the username ends the session, so it redirects to the login page.
 */
@Component({
  selector: 'app-settings-page',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    MatCardModule, MatFormFieldModule, MatInputModule, MatSelectModule,
    MatButtonModule, MatButtonToggleModule, MatSlideToggleModule, TranslocoPipe,
  ],
  template: `
    <h1>{{ 'settings.title' | transloco }}</h1>

    <mat-card class="settings-card">
      <h2>{{ 'settings.language' | transloco }}</h2>
      <mat-form-field appearance="outline">
        <mat-label>{{ 'settings.language' | transloco }}</mat-label>
        <mat-select [value]="userPrefsStore.language()" (selectionChange)="userPrefsStore.setLanguage($event.value)">
          <mat-option value="EN">{{ 'settings.languageEnglish' | transloco }}</mat-option>
          <mat-option value="DE">{{ 'settings.languageGerman' | transloco }}</mat-option>
        </mat-select>
      </mat-form-field>
    </mat-card>

    <mat-card class="settings-card">
      <h2>{{ 'settings.appearance' | transloco }}</h2>
      <div class="setting-row">
        <span>{{ 'settings.theme' | transloco }}</span>
        <mat-button-toggle-group [value]="userPrefsStore.theme()" (change)="userPrefsStore.setTheme($event.value)"
                                 hideSingleSelectionIndicator aria-label="Colour theme">
          <mat-button-toggle value="SYSTEM" [title]="'settings.themeSystem' | transloco">🖥️</mat-button-toggle>
          <mat-button-toggle value="LIGHT" [title]="'settings.themeLight' | transloco">☀️</mat-button-toggle>
          <mat-button-toggle value="DARK" [title]="'settings.themeDark' | transloco">🌙</mat-button-toggle>
        </mat-button-toggle-group>
      </div>
      <mat-slide-toggle [checked]="userPrefsStore.showAgeRatings()"
                        (change)="userPrefsStore.setShowAgeRatings($event.checked)">{{ 'settings.ageRatings' | transloco }}</mat-slide-toggle>
      <mat-slide-toggle [checked]="userPrefsStore.showGermanTitle()"
                        (change)="userPrefsStore.setShowGermanTitle($event.checked)">{{ 'settings.germanTitles' | transloco }}</mat-slide-toggle>
    </mat-card>

    <mat-card class="settings-card">
      <h2>{{ 'settings.account' | transloco }}</h2>
      <p class="text-muted">{{ 'settings.usernameHint' | transloco }}</p>
      <form (submit)="rename($event)" class="setting-row">
        <mat-form-field appearance="outline">
          <mat-label>{{ 'settings.username' | transloco }}</mat-label>
          <input matInput type="text" [value]="username()" (input)="username.set($any($event.target).value)" required />
        </mat-form-field>
        <button matButton="filled" type="submit" [disabled]="!canRename()">{{ 'settings.changeUsername' | transloco }}</button>
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
  protected readonly userPrefsStore = inject(UserPrefsStore);
  private readonly auth = inject(AuthStore);
  private readonly usernameApi = inject(UsernameApi);
  private readonly snackBar = inject(MatSnackBar);
  private readonly transloco = inject(TranslocoService);

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
          this.transloco.translate(err.status === 409 ? 'settings.usernameTaken' : 'settings.usernameError'),
          this.transloco.translate('common.dismiss'),
          { duration: 5000 },
        ),
    });
  }

  private redirectToLogin(): void {
    window.location.href = new URL('../login', document.baseURI).toString();
  }
}
