import { ComponentFixture, TestBed } from '@angular/core/testing';
import { PosterThumb } from './poster-thumb';
import { imdbId } from '../../core/domain';

describe('PosterThumb', () => {
  let fixture: ComponentFixture<PosterThumb>;

  beforeEach(() => {
    TestBed.configureTestingModule({ imports: [PosterThumb] });
    fixture = TestBed.createComponent(PosterThumb);
    fixture.componentRef.setInput('imdbId', imdbId('tt1'));
    fixture.componentRef.setInput('name', 'The Prestige');
  });

  afterEach(() => fixture.destroy());

  const thumb = () => fixture.nativeElement.querySelector('img.poster-thumb') as HTMLImageElement | null;

  it('renders a lazy thumbnail pointing at the poster endpoint', () => {
    fixture.detectChanges();

    const img = thumb();
    expect(img).not.toBeNull();
    expect(img!.getAttribute('src')).toContain('/api/titles/tt1/poster');
    expect(img!.getAttribute('loading')).toBe('lazy');
    expect(img!.getAttribute('alt')).toBe('The Prestige');
  });

  it('hides itself when the image fails to load (no poster)', () => {
    fixture.detectChanges();

    thumb()!.dispatchEvent(new Event('error'));
    fixture.detectChanges();

    expect(thumb()).toBeNull();
  });

  it('shows the hi-res poster in an overlay on hover', () => {
    fixture.detectChanges();

    thumb()!.dispatchEvent(new Event('mouseenter'));
    fixture.detectChanges();

    const hover = document.querySelector('img.poster-hover') as HTMLImageElement | null;
    expect(hover).not.toBeNull();
    expect(hover!.getAttribute('src')).toContain('/api/titles/tt1/poster/full');
  });
});
