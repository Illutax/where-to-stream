import { ChangeDetectionStrategy, Component } from '@angular/core';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { TranslocoPipe } from '@jsverse/transloco';

@Component({
  selector: 'app-loading',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [MatProgressSpinnerModule, TranslocoPipe],
  template: `
    <div class="loading">
      <mat-spinner [diameter]="24" />
      <span class="text-muted">{{ 'common.loading' | transloco }}</span>
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
