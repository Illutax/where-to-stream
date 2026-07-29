/**
 * Splits a title into a main title and subtitle on its first " - ", " – " or ": " separator
 * (the design's own heuristic, since neither TMDB nor IMDb expose a separate subtitle field here).
 * No separator → the whole title is the main title, with no subtitle.
 */
export function splitTitle(title: string): { main: string; sub: string } {
  const parts = title.split(/\s+[-–:]\s+/);
  return parts.length > 1 ? { main: parts[0], sub: parts.slice(1).join(' — ') } : { main: title, sub: '' };
}

/**
 * The poster-tile main-title auto-shrinks by character count instead of truncating —
 * nothing is ever clipped, but a very long title renders smaller.
 * The subtitle steps down alongside it.
 */
export function titleSizeSteps(mainLength: number): { mainSize: number; mainLineHeight: number; subSize: number } {
  const mainSize = mainLength <= 14 ? 20 : mainLength <= 22 ? 17 : mainLength <= 32 ? 14 : mainLength <= 44 ? 12 : 11;
  return {
    mainSize,
    mainLineHeight: mainSize <= 12 ? 1.3 : 1.2,
    subSize: mainSize <= 12 ? 8.5 : 9.5,
  };
}
