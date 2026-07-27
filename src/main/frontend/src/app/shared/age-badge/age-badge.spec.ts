import { ComponentFixture, TestBed } from '@angular/core/testing';
import { AgeBadge } from './age-badge';

describe('AgeBadge', () => {
  let fixture: ComponentFixture<AgeBadge>;

  beforeEach(() => {
    TestBed.configureTestingModule({ imports: [AgeBadge] });
    fixture = TestBed.createComponent(AgeBadge);
  });

  const badge = () => fixture.nativeElement.querySelector('.age-badge') as HTMLElement;

  it('renders an FSK rating with its colour class', () => {
    fixture.componentRef.setInput('rating', { system: 'FSK', label: '16' });
    fixture.detectChanges();

    expect(badge().textContent?.trim()).toBe('16');
    expect(badge().classList).toContain('age-badge--fsk-16');
  });

  it('uses the neutral class for a non-FSK (fallback) rating', () => {
    fixture.componentRef.setInput('rating', { system: 'OTHER', label: 'R' });
    fixture.detectChanges();

    expect(badge().textContent?.trim()).toBe('R');
    expect(badge().classList).toContain('age-badge--other');
  });
});
