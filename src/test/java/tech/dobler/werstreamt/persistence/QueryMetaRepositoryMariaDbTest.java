package tech.dobler.werstreamt.persistence;

import org.junit.jupiter.api.Tag;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.MariaDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Runs the same repository behaviour against a real MariaDB started by Testcontainers, proving
 * the Liquibase XML schema and the JPA mappings work on the production database. Tagged
 * {@code testcontainers} and excluded from the default build (needs a container runtime + image
 * pull access); run with {@code mvn -Ptestcontainers test}. Also skipped where no Docker is found.
 */
@Tag("testcontainers")
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers(disabledWithoutDocker = true)
class QueryMetaRepositoryMariaDbTest extends AbstractQueryMetaRepositoryTests {

    @Container
    @ServiceConnection
    static MariaDBContainer<?> mariadb = new MariaDBContainer<>("mariadb:11");
}
