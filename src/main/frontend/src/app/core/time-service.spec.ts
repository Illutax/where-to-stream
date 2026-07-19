import { TestBed } from '@angular/core/testing';
import { TimeService } from './time-service';

describe('TimeService', () => {
  it('now() delegates to the system clock', () => {
    const service = new TimeService();
    const before = Date.now();
    const now = service.now();
    const after = Date.now();

    expect(now).toBeGreaterThanOrEqual(before);
    expect(now).toBeLessThanOrEqual(after);
  });

  it('nowDate() returns the current date', () => {
    const service = new TimeService();
    expect(service.nowDate().getTime()).toBeCloseTo(Date.now(), -2);
  });

  it('can be substituted with a fixed clock via DI for repeatable tests', () => {
    const fixed = 1_700_000_000_000; // 2023-11-14T22:13:20Z
    TestBed.configureTestingModule({
      providers: [{ provide: TimeService, useValue: { now: () => fixed, nowDate: () => new Date(fixed) } }],
    });

    const service = TestBed.inject(TimeService);

    expect(service.now()).toBe(fixed);
    expect(service.nowDate().toISOString()).toBe('2023-11-14T22:13:20.000Z');
  });
});
