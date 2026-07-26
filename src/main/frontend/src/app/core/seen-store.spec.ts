import { provideHttpClient, withFetch } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { MatSnackBar } from '@angular/material/snack-bar';
import { Subject } from 'rxjs';
import { SeenStore } from './seen-store';
import { imdbId } from './domain';

describe('SeenStore', () => {
  let store: SeenStore;
  let httpMock: HttpTestingController;
  let action: Subject<void>;
  let openCalls: unknown[][];

  beforeEach(() => {
    action = new Subject<void>();
    openCalls = [];
    const snackBar = {
      open: (...args: unknown[]) => {
        openCalls.push(args);
        return { onAction: () => action };
      },
    };

    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(withFetch()),
        provideHttpClientTesting(),
        { provide: MatSnackBar, useValue: snackBar },
      ],
    });
    store = TestBed.inject(SeenStore);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpMock.verify());

  const seenReq = () => httpMock.expectOne((r) => r.url.endsWith('/api/watchlist/tt1/seen'));

  it('applies the change optimistically, highlights the title, then persists it', () => {
    const applied: boolean[] = [];
    store.toggle(imdbId('tt1'), true, (s) => applied.push(s));

    expect(applied).toEqual([true]); // optimistic, before the request resolves
    expect(store.recentlyChanged()).toBe('tt1');

    const req = seenReq();
    expect(req.request.method).toBe('PUT');
    expect(req.request.body).toEqual({ seen: true });
    req.flush(null);

    // success -> an Undo snackbar is offered
    expect(openCalls[0][0]).toBe('Marked as seen');
    expect(openCalls[0][1]).toBe('Undo');
  });

  it('re-toggles when Undo is clicked', () => {
    const applied: boolean[] = [];
    store.toggle(imdbId('tt1'), true, (s) => applied.push(s));
    seenReq().flush(null);

    action.next(); // click "Undo"

    expect(applied).toEqual([true, false]);
    const undo = seenReq();
    expect(undo.request.body).toEqual({ seen: false });
    undo.flush(null);
  });

  it('rolls back and clears the highlight when the server rejects the change', () => {
    const applied: boolean[] = [];
    store.toggle(imdbId('tt1'), true, (s) => applied.push(s));

    seenReq().flush('nope', { status: 500, statusText: 'Server Error' });

    expect(applied).toEqual([true, false]); // optimistic then rolled back
    expect(store.recentlyChanged()).toBeNull();
    expect(openCalls[0][0]).toBe('Could not save the change.');
  });
});
