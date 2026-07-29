import { ChangeDetectionStrategy, Component, computed, inject } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { TranslocoPipe } from '@jsverse/transloco';
import { UserPrefsStore } from '../../core/user-prefs-store';

/**
 * A single icon button that flips the library layout between the poster grid and the list view —
 * the "opt-out" control for the grid-by-default preference.
 * One instance per page is enough since `viewMode` is one global preference shared by every
 * table/grid on the page.
 */
@Component({
  selector: 'app-view-toggle-button',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [MatButtonModule, TranslocoPipe],
  template: `
    <button type="button" matIconButton (click)="toggle()" [attr.aria-label]="label() | transloco">
      {{ userPrefs.viewMode() === 'GRID' ? '☰' : '▦' }}
    </button>
  `,
})
export class ViewToggleButton {
  protected readonly userPrefs = inject(UserPrefsStore);
  protected readonly label = computed(() => (this.userPrefs.viewMode() === 'GRID' ? 'grid.switchToList' : 'grid.switchToGrid'));

  protected toggle(): void {
    this.userPrefs.setViewMode(this.userPrefs.viewMode() === 'GRID' ? 'LIST' : 'GRID');
  }
}
