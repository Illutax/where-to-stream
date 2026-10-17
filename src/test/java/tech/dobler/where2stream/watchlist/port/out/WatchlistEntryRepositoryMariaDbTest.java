package tech.dobler.where2stream.watchlist.port.out;

import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.testcontainers.context.ImportTestcontainers;
import org.testcontainers.junit.jupiter.Testcontainers;
import tech.dobler.where2stream.testing.SharedMariaDb;

/**
 * The same watchlist repository behaviour against a real MariaDB (Testcontainers).
 * Part of the default build, with nothing to switch off: where no container runtime exists
 * {@code disabledWithoutDocker} disables the class and its tests report as skipped.
 * The container is shared with the other {@code *MariaDbTest} classes — see {@link SharedMariaDb}.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers(disabledWithoutDocker = true)
@ImportTestcontainers(SharedMariaDb.class)
class WatchlistEntryRepositoryMariaDbTest extends AbstractWatchlistEntryRepositoryTests {
}
