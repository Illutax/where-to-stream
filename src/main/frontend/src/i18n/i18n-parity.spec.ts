import de from './de.json';
import en from './en.json';

/**
 * Guards the one failure mode the rest of the suite structurally cannot see: the testing setup
 * (`app/testing/transloco-testing.ts`) loads **only** `en.json`, so a key added to one file and
 * forgotten in the other passes every component test, passes the build, passes lint — and shows
 * the raw key ("ebay.link") to whoever switched the language.
 */
describe('i18n', () => {
  /** Every leaf key as a dotted path, e.g. `offers.buyNow`. */
  function keys(node: unknown, prefix = ''): string[] {
    if (typeof node !== 'object' || node === null) {
      return [prefix];
    }
    return Object.entries(node).flatMap(([k, v]) => keys(v, prefix ? `${prefix}.${k}` : k));
  }

  it('has the same keys in German and English', () => {
    const inDe = keys(de);
    const inEn = keys(en);

    // Both directions in one assertion: a missing translation and a leftover key are the same
    // defect seen from either side, and reporting only the first would hide the other.
    expect({
      missingInDe: inEn.filter((k) => !inDe.includes(k)),
      missingInEn: inDe.filter((k) => !inEn.includes(k)),
    }).toEqual({ missingInDe: [], missingInEn: [] });
  });

  it('leaves no value empty', () => {
    // An empty string renders as nothing at all, which looks like a layout bug rather than a
    // missing translation and is correspondingly hard to attribute.
    const valueAt = (file: object, path: string): unknown =>
      path.split('.').reduce<unknown>((node, part) => (node as Record<string, unknown>)?.[part], file);
    const empty = (file: object) => keys(file).filter((k) => !valueAt(file, k));

    expect({ de: empty(de), en: empty(en) }).toEqual({ de: [], en: [] });
  });
});
