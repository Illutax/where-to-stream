package tech.dobler.where2stream.watchlist.adapter.out.persistence;

import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;

/** Runs the watchlist repository behaviour against the default embedded H2 database. */
@DataJpaTest
class WatchlistEntryRepositoryTest extends AbstractWatchlistEntryRepositoryTests {
}
