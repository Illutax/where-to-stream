package tech.dobler.where2stream.testing;

import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.MariaDBContainer;

/**
 * The single MariaDB container every {@code *MariaDbTest} shares.
 *
 * <p>Each of those classes used to declare its own {@code @Container}, and two of them pinned a
 * different image than the other two ({@code mariadb:11} against {@code mariadb:lts-ubi}) — so the
 * suite verified the Liquibase changelog against two server versions, neither of them deliberately
 * chosen, and started four servers to do it. Four container boots plus four Spring contexts plus
 * four Liquibase runs came to roughly 69 s for 18 tests.
 *
 * <p>Sharing one static field makes the {@code @ServiceConnection} identical across the four
 * classes, which makes their merged context configuration identical, which lets Spring's context
 * cache hand all four the same {@code ApplicationContext}. Container, schema migration and
 * EntityManagerFactory therefore happen once for the whole tag.
 *
 * <p>Sharing the database between test classes is safe here because all four are
 * {@code @DataJpaTest}, and that is transactional: every test rolls back. A future test that
 * commits — anything with {@code @Commit}, {@code @Transactional(propagation = NOT_SUPPORTED)} or
 * its own thread — would leak rows into the others and must clean up after itself.
 *
 * <p>The image is deliberately a floating LTS tag rather than a fixed number: this container exists
 * to answer "does our schema work on the MariaDB we ship on", and pinning it would freeze that
 * answer to whichever version happened to be current when the line was written.
 */
public final class SharedMariaDb {

    @ServiceConnection
    static final MariaDBContainer<?> MARIADB = new MariaDBContainer<>("mariadb:lts-ubi");

    private SharedMariaDb() {
    }
}
