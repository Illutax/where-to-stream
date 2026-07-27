import { splitTitle, titleSizeSteps } from './title-split';

describe('splitTitle', () => {
  it('splits on " - " into main and subtitle', () => {
    expect(splitTitle('Die Chroniken von Narnia - Die Reise auf der Morgenröte')).toEqual({
      main: 'Die Chroniken von Narnia',
      sub: 'Die Reise auf der Morgenröte',
    });
  });

  it('splits "Old School - Wir lassen absolut nichts anbrennen"', () => {
    expect(splitTitle('Old School - Wir lassen absolut nichts anbrennen')).toEqual({
      main: 'Old School',
      sub: 'Wir lassen absolut nichts anbrennen',
    });
  });

  it('splits on " – " (en dash) and " : "', () => {
    expect(splitTitle('La Haine – Hass')).toEqual({ main: 'La Haine', sub: 'Hass' });
    expect(splitTitle('Arrival : A First Contact Story')).toEqual({ main: 'Arrival', sub: 'A First Contact Story' });
  });

  it('joins multiple separators with an em dash', () => {
    expect(splitTitle('A - B - C')).toEqual({ main: 'A', sub: 'B — C' });
  });

  it('returns the whole title as main with no subtitle when there is no separator', () => {
    expect(splitTitle('Vertigo')).toEqual({ main: 'Vertigo', sub: '' });
  });
});

describe('titleSizeSteps', () => {
  it('steps down by main-title length', () => {
    expect(titleSizeSteps(14)).toEqual({ mainSize: 20, mainLineHeight: 1.2, subSize: 9.5 });
    expect(titleSizeSteps(22)).toEqual({ mainSize: 17, mainLineHeight: 1.2, subSize: 9.5 });
    expect(titleSizeSteps(32)).toEqual({ mainSize: 14, mainLineHeight: 1.2, subSize: 9.5 });
    expect(titleSizeSteps(44)).toEqual({ mainSize: 12, mainLineHeight: 1.3, subSize: 8.5 });
    expect(titleSizeSteps(45)).toEqual({ mainSize: 11, mainLineHeight: 1.3, subSize: 8.5 });
  });

  it("matches the mock's own examples", () => {
    // splitTitle('Die Chroniken von Narnia - ...').main has length 24 -> the 14/22/32/44 tier is ≤32
    expect(titleSizeSteps('Die Chroniken von Narnia'.length)).toEqual({ mainSize: 14, mainLineHeight: 1.2, subSize: 9.5 });
    // 'Old School'.length === 10 -> ≤14
    expect(titleSizeSteps('Old School'.length)).toEqual({ mainSize: 20, mainLineHeight: 1.2, subSize: 9.5 });
  });
});
