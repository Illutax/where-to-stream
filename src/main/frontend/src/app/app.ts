import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { RouterOutlet } from '@angular/router';
import { ListSelectionStore } from './core/list-selection-store';
import { Navbar } from './shared/navbar/navbar';

@Component({
  selector: 'app-root',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [RouterOutlet, Navbar],
  template: `
    <app-navbar [currentList]="store.current()" />
    <main class="app-container">
      <router-outlet />
    </main>
  `,
})
export class App {
  protected readonly store = inject(ListSelectionStore);

  constructor() {
    this.store.load();
  }
}
