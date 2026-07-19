import { ChangeDetectionStrategy, Component, input, output, signal } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';

/**
 * Presentational list selector. Emits the chosen list name; performs no data loading. Uses a
 * native `<select matNativeControl>` inside a Material form field (no CDK overlay).
 */
@Component({
  selector: 'app-list-picker',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [MatFormFieldModule, MatInputModule, MatButtonModule],
  template: `
    <form (submit)="onSubmit($event)">
      <mat-form-field appearance="outline">
        <mat-label>Choose a list</mat-label>
        <select matNativeControl [value]="current()" (change)="picked.set($any($event.target).value)">
          @for (name of available(); track name) {
            <option [value]="name" [selected]="name === current()">{{ name }}</option>
          }
        </select>
      </mat-form-field>
      <div>
        <button matButton="filled" type="submit" [disabled]="disabled()">Submit</button>
      </div>
    </form>
  `,
})
export class ListPicker {
  readonly current = input.required<string>();
  readonly available = input.required<string[]>();
  readonly disabled = input<boolean>(false);

  readonly listChange = output<string>();

  protected readonly picked = signal<string | null>(null);

  protected onSubmit(event: Event): void {
    event.preventDefault();
    const chosen = this.picked() ?? this.current();
    this.listChange.emit(chosen);
  }
}
