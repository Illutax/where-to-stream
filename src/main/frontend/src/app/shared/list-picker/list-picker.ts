import { ChangeDetectionStrategy, Component, input, output, signal } from '@angular/core';

/** Presentational list selector. Emits the chosen list name; performs no data loading. */
@Component({
  selector: 'app-list-picker',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <form (submit)="onSubmit($event)">
      <label for="list" class="form-label">Choose a list:</label>
      <select id="list" class="form-select mb-2" [value]="current()"
              (change)="picked.set($any($event.target).value)">
        @for (name of available(); track name) {
          <option [value]="name" [selected]="name === current()">{{ name }}</option>
        }
      </select>
      <button type="submit" class="btn btn-primary" [disabled]="disabled()">Submit</button>
    </form>
  `,
})
export class ListPicker {
  readonly current = input.required<string>();
  readonly available = input.required<string[]>();
  readonly disabled = input<boolean>(false);

  readonly change = output<string>();

  protected readonly picked = signal<string | null>(null);

  protected onSubmit(event: Event): void {
    event.preventDefault();
    const chosen = this.picked() ?? this.current();
    this.change.emit(chosen);
  }
}
