import { ComponentFixture, TestBed } from '@angular/core/testing';
import { CatalogSkeleton } from './catalog-skeleton';

describe('CatalogSkeleton', () => {
  let fixture: ComponentFixture<CatalogSkeleton>;

  beforeEach(() => {
    TestBed.configureTestingModule({ imports: [CatalogSkeleton] });
    fixture = TestBed.createComponent(CatalogSkeleton);
  });

  it('renders tilesPerRow x 3 poster placeholders in a grid sized to tilesPerRow, for GRID mode', () => {
    fixture.componentRef.setInput('viewMode', 'GRID');
    fixture.componentRef.setInput('tilesPerRow', 5);
    fixture.detectChanges();

    const grid = fixture.nativeElement.querySelector('.tile-grid') as HTMLElement;
    expect(grid).not.toBeNull();
    expect(grid.style.getPropertyValue('--tiles-per-row')).toBe('5');
    expect(fixture.nativeElement.querySelectorAll('app-title-tile-skeleton').length).toBe(15);
    expect(fixture.nativeElement.querySelector('.row-skeleton')).toBeNull();
  });

  it('renders row placeholders for LIST mode, independent of tilesPerRow', () => {
    fixture.componentRef.setInput('viewMode', 'LIST');
    fixture.componentRef.setInput('tilesPerRow', 6);
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelectorAll('.row-skeleton').length).toBe(8);
    expect(fixture.nativeElement.querySelector('.tile-grid')).toBeNull();
  });
});
