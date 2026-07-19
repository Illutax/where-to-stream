import { ChangeDetectionStrategy, Component } from '@angular/core';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';

@Component({
  selector: 'app-loading',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [MatProgressSpinnerModule],
  template: `
    <div class="loading">
      <mat-spinner [diameter]="24" />
      <span class="text-muted">Loading…</span>
    </div>
  `,
  styles: `
    .loading {
      display: flex;
      align-items: center;
      gap: 0.5rem;
      margin: 1rem 0;
    }
  `,
})
export class Loading {}
