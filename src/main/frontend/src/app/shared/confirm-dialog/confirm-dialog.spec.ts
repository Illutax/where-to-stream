import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MAT_DIALOG_DATA, MatDialogRef } from '@angular/material/dialog';
import { translocoTesting } from '../../testing/transloco-testing';
import { ConfirmDialog, ConfirmDialogData } from './confirm-dialog';

describe('ConfirmDialog', () => {
  let fixture: ComponentFixture<ConfirmDialog>;
  let dialogRef: { close: ReturnType<typeof vi.fn> };

  const data: ConfirmDialogData = {
    title: 'Remove watched titles?',
    message: 'This removes every watched title from your watchlist. This cannot be undone.',
    confirmLabel: 'Remove',
  };

  beforeEach(() => {
    dialogRef = { close: vi.fn() };
    TestBed.configureTestingModule({
      imports: [ConfirmDialog, translocoTesting()],
      providers: [
        { provide: MatDialogRef, useValue: dialogRef },
        { provide: MAT_DIALOG_DATA, useValue: data },
      ],
    });
    fixture = TestBed.createComponent(ConfirmDialog);
    fixture.detectChanges();
  });

  const buttons = () => Array.from(fixture.nativeElement.querySelectorAll('button')) as HTMLButtonElement[];

  it('renders the title, message, and confirm label', () => {
    expect(fixture.nativeElement.textContent).toContain('Remove watched titles?');
    expect(fixture.nativeElement.textContent).toContain('This removes every watched title');
    expect(fixture.nativeElement.textContent).toContain('Remove');
  });

  it('closes with true when confirmed', () => {
    buttons()[1].click();
    expect(dialogRef.close).toHaveBeenCalledWith(true);
  });

  it('closes with false when cancelled', () => {
    buttons()[0].click();
    expect(dialogRef.close).toHaveBeenCalledWith(false);
  });
});
