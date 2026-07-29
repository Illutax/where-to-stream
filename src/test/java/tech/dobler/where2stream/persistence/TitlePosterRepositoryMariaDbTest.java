package tech.dobler.where2stream.persistence;

import org.junit.jupiter.api.Tag;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.MariaDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Runs the poster repository behaviour against a real MariaDB started by Testcontainers, proving
 * the BLOB columns round-trip on the production database. Tagged {@code testcontainers} and
 * excluded from the default build; run with {@code mvn -Ptestcontainers test}.
 */
@Tag("testcontainers")
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers(disabledWithoutDocker = true)
class TitlePosterRepositoryMariaDbTest extends AbstractTitlePosterRepositoryTests {

    @Container
    @ServiceConnection
    static MariaDBContainer<?> mariadb = new MariaDBContainer<>("mariadb:lts-ubi");
}
