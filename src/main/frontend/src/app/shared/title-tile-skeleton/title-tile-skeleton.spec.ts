import { ComponentFixture, TestBed } from '@angular/core/testing';
import { TitleTileSkeleton } from './title-tile-skeleton';

describe('TitleTileSkeleton', () => {
  let fixture: ComponentFixture<TitleTileSkeleton>;

  beforeEach(() => {
    TestBed.configureTestingModule({ imports: [TitleTileSkeleton] });
    fixture = TestBed.createComponent(TitleTileSkeleton);
  });

  it('renders a placeholder box', () => {
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('.title-tile-skeleton')).not.toBeNull();
  });
});
