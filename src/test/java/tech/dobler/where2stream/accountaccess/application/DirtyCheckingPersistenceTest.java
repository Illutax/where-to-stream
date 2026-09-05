package tech.dobler.where2stream.accountaccess.application;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import tech.dobler.where2stream.accountaccess.application.command.ThemeUpdateCommand;
import tech.dobler.where2stream.accountaccess.domain.Theme;
import tech.dobler.where2stream.accountaccess.port.out.AppUserRepository;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Proves that removing the explicit {@code save()} calls (ADR-0018) actually still writes.
 *
 * <p>This test exists because the unit tests <em>cannot</em> prove it. With a mocked repository
 * there is no persistence context and no flush; the most those tests can say is "no {@code save()}
 * was called", which is a statement about the mechanism, not about the outcome. If the transaction
 * boundary were wrong — the entity loaded outside it, or the method not transactional — every one
 * of them would stay green while the change quietly vanished. That is the failure mode ADR-0018
 * names as the price of this convention, and this is the test that pays it.
 *
 * <p>Deliberately <strong>not</strong> annotated {@code @Transactional}: a transactional test would
 * wrap the service call in the test's own transaction and roll it back, and the assertion would
 * read the change straight out of the shared persistence context — passing without a single
 * {@code UPDATE} ever reaching the database. Here the service opens and commits its own
 * transaction, and the assertion re-reads afterwards.
 */
@SpringBootTest
class DirtyCheckingPersistenceTest {

    @Autowired
    private UserPreferencesService userPreferencesService;
    @Autowired
    private AppUserRepository users;

    @Test
    void aMutationWithoutAnExplicitSaveReachesTheDatabase() {
        final var username = users.findAll().getFirst().getUsername();
        final var original = userPreferencesService.preferencesFor(username).theme();
        final var changed = original == Theme.DARK ? Theme.LIGHT : Theme.DARK;

        try {
            userPreferencesService.updateTheme(new ThemeUpdateCommand(username, changed));

            // Re-read after the service's own transaction committed. The service never calls
            // save() — if dirty checking were not doing the work, this would still be `original`.
            assertThat(users.findByUsername(username)).get()
                    .extracting(user -> user.getTheme())
                    .isEqualTo(changed);
        } finally {
            userPreferencesService.updateTheme(new ThemeUpdateCommand(username, original));
        }
    }
}
