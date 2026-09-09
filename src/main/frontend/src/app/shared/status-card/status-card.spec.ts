import { ComponentFixture, TestBed } from '@angular/core/testing';
import { translocoTesting } from '../../testing/transloco-testing';
import { StatusCard } from './status-card';

describe('StatusCard', () => {
  let fixture: ComponentFixture<StatusCard>;

  beforeEach(() => {
    TestBed.configureTestingModule({ imports: [StatusCard, translocoTesting()] });
    fixture = TestBed.createComponent(StatusCard);
  });

  it('renders the version, server start time and title count', () => {
    fixture.componentRef.setInput('status', { version: '1.2.3', serverStart: '2026-01-01T00:00:00Z', titles: 1234 });
    fixture.detectChanges();

    const text = fixture.nativeElement.textContent;
    expect(text).toContain('1.2.3');
    expect(text).toContain('2026-01-01');
    expect(text).toContain('1234');
    expect(fixture.nativeElement.querySelector('.skeleton-bar')).toBeNull();
  });

  it('shows skeleton lines while loading, with the card title still visible', () => {
    fixture.componentRef.setInput('loading', true);
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('General Information');
    expect(fixture.nativeElement.querySelectorAll('.skeleton-bar').length).toBe(2);
  });
});
