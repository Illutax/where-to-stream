package tech.dobler.where2stream.watchlist.adapter.out.persistence;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.transaction.annotation.Transactional;
import tech.dobler.where2stream.shared.domain.ImdbId;
import tech.dobler.where2stream.shared.domain.ReleaseYear;
import tech.dobler.where2stream.watchlist.domain.WatchlistDate;
import tech.dobler.where2stream.watchlist.domain.WatchlistEntry;
import tech.dobler.where2stream.accountaccess.adapter.out.persistence.AppUserRepository;
import tech.dobler.where2stream.accountaccess.domain.AppUser;
import tech.dobler.where2stream.accountaccess.domain.Role;

import java.net.URI;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Repository behaviour for {@link WatchlistEntryRepository}, run against both H2 and a
 * Testcontainers MariaDB via the concrete subclasses. Watchlist rows FK to {@code app_user}, so a
 * user is seeded first.
 */
@Transactional
public abstract class AbstractWatchlistEntryRepositoryTests {

    private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");

    @Autowired
    private WatchlistEntryRepository sut;
    @Autowired
    private AppUserRepository users;
    @Autowired
    private TestEntityManager entityManager;

    private UUID alice;
    private UUID bob;

    @BeforeEach
    void setUp() {
        sut.deleteAll();
        users.deleteAll();
        alice = users.save(AppUser.local("alice", "{noop}x", "a@x", Set.of(Role.USER), NOW)).getId();
        bob = users.save(AppUser.local("bob", "{noop}x", "b@x", Set.of(Role.USER), NOW)).getId();
        flushAndClear();
    }

    private WatchlistEntry entry(UUID userId, String imdbId, boolean rated) {
        return WatchlistEntry.of(userId, ImdbId.of(imdbId), "Title " + imdbId,
                URI.create("https://www.imdb.com/title/" + imdbId + "/"), WatchlistDate.of("2020-01-01"), rated, ReleaseYear.of(2020), NOW);
    }

    private static ImdbId id(String imdbId) {
        return ImdbId.of(imdbId);
    }

    private void flushAndClear() {
        entityManager.flush();
        entityManager.clear();
    }

    @Test
    void savesAndFindsEntriesScopedToTheUser() {
        sut.save(entry(alice, "tt1", true));
        sut.save(entry(alice, "tt2", false));
        sut.save(entry(bob, "tt3", true));
        flushAndClear();

        assertThat(sut.findByUserId(alice)).extracting(WatchlistEntry::getImdbId)
                .containsExactlyInAnyOrder(id("tt1"), id("tt2"));
        assertThat(sut.findByUserId(bob)).extracting(WatchlistEntry::getImdbId).containsExactly(id("tt3"));
        assertThat(sut.countByUserId(alice)).isEqualTo(2);
    }

    @Test
    void findsRatedEntriesAndByImdbId() {
        sut.save(entry(alice, "tt1", true));
        sut.save(entry(alice, "tt2", false));
        flushAndClear();

        assertThat(sut.findByUserIdAndRatedTrue(alice)).extracting(WatchlistEntry::getImdbId).containsExactly(id("tt1"));
        assertThat(sut.findByUserIdAndImdbId(alice, id("tt2"))).isPresent();
        assertThat(sut.findByUserIdAndImdbId(bob, id("tt1"))).isEmpty();
    }

    @Test
    void distinctImdbIdsAcrossAllUsersForGlobalCacheMaintenance() {
        sut.save(entry(alice, "tt1", true));
        sut.save(entry(bob, "tt1", false)); // same title, different user
        sut.save(entry(bob, "tt9", true));
        flushAndClear();

        assertThat(sut.findDistinctImdbIds()).containsExactlyInAnyOrder(id("tt1"), id("tt9"));
        assertThat(sut.findDistinctImdbIdsRated()).containsExactlyInAnyOrder(id("tt1"), id("tt9"));
    }

    @Test
    void deleteByUserIdOnlyClearsThatUsersList() {
        sut.save(entry(alice, "tt1", true));
        sut.save(entry(bob, "tt2", true));
        flushAndClear();

        sut.deleteByUserId(alice);
        flushAndClear();

        assertThat(sut.findByUserId(alice)).isEmpty();
        assertThat(sut.findByUserId(bob)).hasSize(1);
    }

    @Test
    void theSameTitleCannotBeAddedTwiceForOneUser() {
        sut.save(entry(alice, "tt1", true));
        flushAndClear();

        assertThatThrownBy(() -> {
            sut.save(entry(alice, "tt1", false));
            flushAndClear();
        }).isInstanceOf(Exception.class); // unique (user_id, imdb_id) violation
    }
}
