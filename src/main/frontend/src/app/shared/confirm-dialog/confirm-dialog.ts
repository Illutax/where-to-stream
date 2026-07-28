import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MAT_DIALOG_DATA, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { TranslocoPipe } from '@jsverse/transloco';

/** Content passed to {@link ConfirmDialog} via {@code MatDialog.open(ConfirmDialog, { data })}. */
export interface ConfirmDialogData {
  title: string;
  message: string;
  confirmLabel: string;
}

/**
 * A generic yes/no confirmation dialog for destructive actions. Closes with {@code true} on
 * confirm, {@code false} on cancel/backdrop-dismiss.
 */
@Component({
  selector: 'app-confirm-dialog',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [MatDialogModule, MatButtonModule, TranslocoPipe],
  template: `
    <h2 mat-dialog-title>{{ data.title }}</h2>
    <mat-dialog-content>{{ data.message }}</mat-dialog-content>
    <mat-dialog-actions align="end">
      <button matButton (click)="dialogRef.close(false)">{{ 'common.cancel' | transloco }}</button>
      <button matButton="filled" (click)="dialogRef.close(true)">{{ data.confirmLabel }}</button>
    </mat-dialog-actions>
  `,
})
export class ConfirmDialog {
  protected readonly dialogRef = inject(MatDialogRef<ConfirmDialog, boolean>);
  protected readonly data = inject<ConfirmDialogData>(MAT_DIALOG_DATA);
}
