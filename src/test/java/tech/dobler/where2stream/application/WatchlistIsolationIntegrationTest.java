package tech.dobler.where2stream.application;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import tech.dobler.where2stream.watchlist.application.dto.WatchlistImportResultDto;
import tech.dobler.where2stream.watchlist.domain.ImdbEntry;
import tech.dobler.where2stream.shared.domain.ImdbId;
import tech.dobler.where2stream.accountaccess.domain.Role;
import tech.dobler.where2stream.accountaccess.domain.AppUser;
import tech.dobler.where2stream.accountaccess.adapter.out.persistence.AppUserRepository;
import tech.dobler.where2stream.watchlist.application.WatchlistCatalog;
import tech.dobler.where2stream.watchlist.application.WatchlistImportService;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Full-stack proof that watchlists are isolated per user: an import for one user never leaks into
 * another's list, and per-user operations (re-import full-sync, clear) touch only that user. Runs
 * the real {@link WatchlistImportService} against H2 (imports are DB-only — no scraping — so this
 * needs no network), then reads back through {@link WatchlistCatalog}.
 */
@SpringBootTest
class WatchlistIsolationIntegrationTest {

    private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");

    @Autowired
    private WatchlistImportService importService;
    @Autowired
    private WatchlistCatalog catalog;
    @Autowired
    private AppUserRepository users;

    private UUID alice;
    private UUID bob;

    @BeforeEach
    void seedUsers() {
        alice = users.save(AppUser.local("alice-iso", "{noop}x", "alice-iso@x", Set.of(Role.USER), NOW)).getId();
        bob = users.save(AppUser.local("bob-iso", "{noop}x", "bob-iso@x", Set.of(Role.USER), NOW)).getId();
    }

    @AfterEach
    void cleanUp() {
        // clear() is the production per-user delete; then remove the seeded accounts.
        importService.clear(alice);
        importService.clear(bob);
        users.deleteById(alice);
        users.deleteById(bob);
    }

    private record Row(String imdbId, String title, boolean rated) {
    }

    private static Row row(String imdbId, String title, boolean rated) {
        return new Row(imdbId, title, rated);
    }

    /** Builds a minimal IMDb CSV export stream from the given rows. */
    private static InputStream csv(Row... rows) {
        final var sb = new StringBuilder("Position,Const,Created,Modified,Description,Title,"
                + "Original Title,URL,Title Type,IMDb Rating,Runtime (mins),Year,Genres,Num Votes,"
                + "Release Date,Directors,Your Rating,Date Rated\n");
        int pos = 1;
        for (Row r : rows) {
            sb.append(pos++).append(',').append(r.imdbId()).append(",2012-06-22,2012-06-22,,\"")
                    .append(r.title()).append("\",\"").append(r.title())
                    .append("\",https://www.imdb.com/title/").append(r.imdbId())
                    .append("/,Movie,8.5,130,2006,\"Drama\",1000,2006-10-20,\"Dir\",")
                    .append(r.rated() ? "10" : "").append(",2012-06-22\n");
        }
        return new ByteArrayInputStream(sb.toString().getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void eachUserSeesOnlyTheirOwnImportedTitles() {
        importService.importCsv(alice, csv(row("tt1", "Alpha", true), row("tt2", "Beta", false)));
        importService.importCsv(bob, csv(row("tt3", "Gamma", true)));

        assertThat(catalog.findAll(alice)).extracting(ImdbEntry::imdbId)
                .containsExactlyInAnyOrder(ImdbId.of("tt1"), ImdbId.of("tt2"));
        assertThat(catalog.findAll(bob)).extracting(ImdbEntry::imdbId).containsExactly(ImdbId.of("tt3"));
        assertThat(importService.status(alice).count()).isEqualTo(2);
        assertThat(importService.status(bob).count()).isEqualTo(1);
    }

    @Test
    void theSameTitleOnBothWatchlistsIsStoredIndependently() {
        // Both users have tt1, but alice rated it and bob did not — the rows are per-user.
        importService.importCsv(alice, csv(row("tt1", "Shared", true)));
        importService.importCsv(bob, csv(row("tt1", "Shared", false)));

        assertThat(catalog.findByImdb(alice, ImdbId.of("tt1"))).get().extracting(ImdbEntry::isRated).isEqualTo(true);
        assertThat(catalog.findByImdb(bob, ImdbId.of("tt1"))).get().extracting(ImdbEntry::isRated).isEqualTo(false);
        assertThat(catalog.count(alice)).isEqualTo(1);
        assertThat(catalog.count(bob)).isEqualTo(1);
    }

    @Test
    void clearingOneUserLeavesTheOtherIntact() {
        importService.importCsv(alice, csv(row("tt1", "Alpha", true), row("tt2", "Beta", false)));
        importService.importCsv(bob, csv(row("tt3", "Gamma", true)));

        importService.clear(alice);

        assertThat(catalog.count(alice)).isZero();
        assertThat(catalog.findAll(bob)).extracting(ImdbEntry::imdbId).containsExactly(ImdbId.of("tt3"));
    }

    @Test
    void aFullSyncReimportOnlyAffectsThatUser() {
        importService.importCsv(alice, csv(row("tt1", "Alpha", true), row("tt2", "Beta", false)));
        importService.importCsv(bob, csv(row("tt1", "Alpha", true), row("tt3", "Gamma", true)));

        // Re-import for alice: keep tt2, drop tt1, add tt4. Bob shares tt1 but must be untouched.
        final var result = importService.importCsv(alice, csv(row("tt2", "Beta", false), row("tt4", "Delta", false)));

        assertThat(result).extracting(WatchlistImportResultDto::added, WatchlistImportResultDto::removed)
                .containsExactly(1, 1);
        assertThat(catalog.findAll(alice)).extracting(ImdbEntry::imdbId)
                .containsExactlyInAnyOrder(ImdbId.of("tt2"), ImdbId.of("tt4"));
        // Bob still has both his titles — alice removing tt1 did not remove bob's tt1.
        assertThat(catalog.findAll(bob)).extracting(ImdbEntry::imdbId)
                .containsExactlyInAnyOrder(ImdbId.of("tt1"), ImdbId.of("tt3"));
    }
}
