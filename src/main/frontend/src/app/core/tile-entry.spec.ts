import { imdbId, releaseYear, watchlistDate } from './domain';
import { flatrateToTile, overviewToTile, paidToTile } from './tile-entry';

describe('tile-entry adapters', () => {
  it('overviewToTile normalizes an OverviewEntry, keeping the year numeric', () => {
    const tile = overviewToTile({
      isRated: true,
      name: 'Vertigo',
      imdbId: imdbId('tt1'),
      year: releaseYear(1958),
      added: watchlistDate('2024-11-03'),
      services: 'Netflix',
    });

    expect(tile).toEqual({
      imdbId: imdbId('tt1'),
      name: 'Vertigo',
      year: releaseYear(1958),
      added: watchlistDate('2024-11-03'),
      isRated: true,
    });
  });

  it('overviewToTile passes an unreleased title through as year 0', () => {
    const tile = overviewToTile({
      isRated: false,
      name: 'Upcoming',
      imdbId: imdbId('tt2'),
      year: releaseYear(0),
      added: watchlistDate('2026-01-01'),
      services: null,
    });

    expect(tile.year).toBe(releaseYear(0));
  });

  it('flatrateToTile normalizes a FlatrateEntry the same way', () => {
    const tile = flatrateToTile({
      isRated: false,
      name: 'Stalker',
      imdbId: imdbId('tt3'),
      year: releaseYear(1979),
      added: watchlistDate('2025-01-18'),
    });

    expect(tile).toEqual({
      imdbId: imdbId('tt3'),
      name: 'Stalker',
      year: releaseYear(1979),
      added: watchlistDate('2025-01-18'),
      isRated: false,
    });
  });

  it('paidToTile is now the same shape as the others: the server sends a number', () => {
    const tile = paidToTile({
      name: 'Drive',
      imdbId: imdbId('tt4'),
      price: '3,99 €',
      added: watchlistDate('2026-02-14'),
      isRated: true,
      year: releaseYear(0),
      languages: 'DE, EN',
    });

    expect(tile).toEqual({
      imdbId: imdbId('tt4'),
      name: 'Drive',
      year: releaseYear(0),
      added: watchlistDate('2026-02-14'),
      isRated: true,
    });
  });
});
