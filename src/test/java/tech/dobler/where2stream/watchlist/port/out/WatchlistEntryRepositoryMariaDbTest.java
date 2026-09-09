package tech.dobler.where2stream.watchlist.port.out;

import org.junit.jupiter.api.Tag;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.testcontainers.context.ImportTestcontainers;
import org.testcontainers.junit.jupiter.Testcontainers;
import tech.dobler.where2stream.testing.SharedMariaDb;

/**
 * The same watchlist repository behaviour against a real MariaDB (Testcontainers).
 * Part of the default build; leave it out with {@code -Pno-testcontainers} where no container
 * runtime exists.
 * The container is shared with the other {@code *MariaDbTest} classes — see {@link SharedMariaDb}.
 */
@Tag("testcontainers")
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers(disabledWithoutDocker = true)
@ImportTestcontainers(SharedMariaDb.class)
class WatchlistEntryRepositoryMariaDbTest extends AbstractWatchlistEntryRepositoryTests {
}
