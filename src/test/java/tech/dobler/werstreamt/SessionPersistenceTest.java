package tech.dobler.werstreamt;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.session.SessionRepository;
import org.springframework.session.jdbc.JdbcIndexedSessionRepository;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies HTTP sessions are persisted in the database (Spring Session JDBC) so a login survives
 * an application restart: the active {@link SessionRepository} is the JDBC-backed one, and the
 * Liquibase-created SPRING_SESSION schema round-trips a saved session.
 */
@SpringBootTest
class SessionPersistenceTest {

    @Autowired
    private JdbcIndexedSessionRepository sessionRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private int sessionCount() {
        return jdbcTemplate.queryForObject("select count(*) from SPRING_SESSION", Integer.class);
    }

    @Test
    void theActiveSessionStoreIsJdbcBacked() {
        // Injecting JdbcIndexedSessionRepository proves the JDBC store is the active SessionRepository,
        // and the Liquibase-owned Spring Session schema exists and is queryable.
        assertThat(sessionRepository).isNotNull();
        assertThat(sessionCount()).isNotNegative();
    }

    @Test
    void savedSessionsAreStoredInTheDatabase() {
        final int before = sessionCount();

        final var session = createAndSaveSession();

        assertThat(sessionCount()).isGreaterThan(before);
        sessionRepository.deleteById(session);
        assertThat(sessionCount()).isEqualTo(before);
    }

    /** Uses the raw repository so the package-private JdbcSession type need not be named. */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private String createAndSaveSession() {
        final SessionRepository repo = this.sessionRepository;
        final var session = repo.createSession();
        session.setAttribute("k", "v");
        repo.save(session);
        return session.getId();
    }
}
