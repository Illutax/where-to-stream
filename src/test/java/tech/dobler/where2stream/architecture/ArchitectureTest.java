package tech.dobler.where2stream.architecture;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import org.springframework.data.repository.Repository;
import tech.dobler.where2stream.watchlist.domain.ImdbEntry;
import tech.dobler.where2stream.watchlist.domain.WatchlistDate;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Date;

import static com.tngtech.archunit.base.DescribedPredicate.not;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.belongToAnyOf;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAPackage;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.Architectures.layeredArchitecture;

/**
 * Enforced architecture rules. Analyses production classes only (tests may read the real clock
 * and construct fixtures freely).
 */
@AnalyzeClasses(packages = "tech.dobler.where2stream", importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureTest {

    /**
     * Time must be read through the {@code TimeService} facade, never via static {@code now()}
     * calls (ADR-0003). The only exception is the facade's production implementation.
     */
    @ArchTest
    static final ArchRule time_is_read_only_through_the_facade = noClasses()
            .that().doNotHaveFullyQualifiedName("tech.dobler.where2stream.shared.time.SystemTimeService")
            .should().callMethod(Instant.class, "now")
            .orShould().callMethod(LocalDate.class, "now")
            .orShould().callMethod(LocalDateTime.class, "now")
            .orShould().callMethod(System.class, "currentTimeMillis")
            .orShould().callConstructor(Date.class)
            .because("time must be read through TimeService, not static now() calls (ADR-0003)");

    /**
     * The authenticated user is resolved in the presentation layer (from the {@code Authentication})
     * and passed down as a username / userId; the layers below never read the Spring Security
     * {@code SecurityContext} (ADR-0007). This keeps the watchlist queries testable with a plain
     * {@code UUID} and the lower layers free of Spring Security.
     */
    @ArchTest
    static final ArchRule security_context_is_only_read_in_the_presentation_layer = noClasses()
            .that().resideInAnyPackage("..application..", "..services..", "..persistence..", "..domain..")
            .should().dependOnClassesThat()
            .haveFullyQualifiedName("org.springframework.security.core.context.SecurityContextHolder")
            .because("lower layers receive the username/userId from the presentation layer instead "
                    + "of reading the SecurityContext (ADR-0007)");

    /**
     * Bounded-context isolation: as each context is carved out of the old technical layering
     * (see {@code docs/adr} for the restructuring), classes outside {@code accountaccess} may
     * depend on it only through its published inbound port ({@code CurrentUserPort}, under
     * {@code port.in}), never through its domain/application/adapter internals directly — and
     * NOT through {@code port.out} either: outbound ports (e.g. a Spring Data repository, see the
     * rule below) are the context's own dependency on its database/external systems, not something
     * other contexts are meant to call. One isolation rule gets added per migrated context.
     * {@code shared..} is exempt: {@code ApiExceptionHandler} deliberately maps every context's own
     * exception types (a cross-cutting concern the shared kernel is meant to know about), which is
     * a different thing from one bounded context depending on another's internals.
     */
    @ArchTest
    static final ArchRule accountaccess_is_only_accessed_through_its_published_ports = noClasses()
            .that().resideOutsideOfPackage("..accountaccess..")
            .and().resideOutsideOfPackage("..shared..")
            .should().dependOnClassesThat(
                    resideInAPackage("..accountaccess..")
                            .and(not(resideInAPackage("..accountaccess.port.in..")))
            )
            .because("other bounded contexts may depend on accountaccess only through its "
                    + "published inbound port (CurrentUserPort), not its internals — including its "
                    + "own outbound ports (e.g. AppUserRepository)");

    /**
     * Same isolation rule as above, for the Watchlist context (published port:
     * {@code WatchlistCatalogPort}, under {@code port.in}) — with one addition:
     * {@link ImdbEntry}/{@link WatchlistDate} are the read-model value types
     * {@code WatchlistCatalogPort}'s own methods return, so they're part of its published contract
     * too, not internals like the {@code WatchlistEntry} JPA entity or watchlist's exceptions.
     */
    @ArchTest
    static final ArchRule watchlist_is_only_accessed_through_its_published_ports = noClasses()
            .that().resideOutsideOfPackage("..watchlist..")
            .and().resideOutsideOfPackage("..shared..")
            .should().dependOnClassesThat(
                    resideInAPackage("..watchlist..")
                            .and(not(resideInAPackage("..watchlist.port.in..")))
                            .and(not(belongToAnyOf(ImdbEntry.class, WatchlistDate.class)))
            )
            .because("other bounded contexts may depend on watchlist only through its published "
                    + "inbound port (WatchlistCatalogPort) plus the read-model types it returns "
                    + "(ImdbEntry, WatchlistDate) — not watchlist's other internals, including its "
                    + "own outbound port (WatchlistEntryRepository)");

    /**
     * Same isolation rule again, for Title Catalog (published port: {@code TitleCacheMaintenancePort},
     * under {@code port.in}). Unlike Watchlist's port, this one's only method takes/returns shared
     * kernel types ({@code ImdbId}) — nothing titlecatalog-local leaks through it, so no extra
     * value-type exemption is needed here.
     */
    @ArchTest
    static final ArchRule titlecatalog_is_only_accessed_through_its_published_ports = noClasses()
            .that().resideOutsideOfPackage("..titlecatalog..")
            .and().resideOutsideOfPackage("..shared..")
            .should().dependOnClassesThat(
                    resideInAPackage("..titlecatalog..")
                            .and(not(resideInAPackage("..titlecatalog.port.in..")))
            )
            .because("other bounded contexts may depend on titlecatalog only through its published "
                    + "inbound port (TitleCacheMaintenancePort), not its internals — including its "
                    + "own outbound ports (PosterSource, TitleMetaRepository, TitlePosterRepository)");

    /**
     * Same isolation rule again, for Streaming Availability. Unlike the other three contexts, it
     * publishes no inbound port at all: it only ever reaches <em>out</em> to watchlist's and title
     * catalog's published ports (WatchlistCatalogPort, TitleCacheMaintenancePort) — nothing outside
     * this context currently needs to call into it. The rule still guards the boundary going
     * forward even though there's nothing in {@code port.in} to exempt yet.
     */
    @ArchTest
    static final ArchRule streamingavailability_is_only_accessed_through_its_published_ports = noClasses()
            .that().resideOutsideOfPackage("..streamingavailability..")
            .and().resideOutsideOfPackage("..shared..")
            .should().dependOnClassesThat(
                    resideInAPackage("..streamingavailability..")
                            .and(not(resideInAPackage("..streamingavailability.port.in..")))
            )
            .because("nothing outside streaming availability should depend on its internals — it "
                    + "has no published inbound port because nothing currently needs to call into it");

    /**
     * A Spring Data repository interface is itself the outbound port to the database: Spring Data
     * generates the adapter (a runtime proxy) directly from the interface, so there's no separate
     * hand-written adapter class the way there is for e.g. {@code PosterSource}. All four bounded
     * contexts have now migrated, so this applies everywhere (old flat {@code persistence} classes
     * are gone).
     */
    @ArchTest
    static final ArchRule spring_data_repositories_are_the_port_not_the_adapter = classes()
            .that().areAssignableTo(Repository.class)
            .and().resideInAnyPackage("..accountaccess..", "..watchlist..", "..titlecatalog..", "..streamingavailability..")
            .should().resideInAPackage("..port.out..")
            .because("the repository interface is the outbound port; JPA supplies the adapter as a "
                    + "runtime proxy, so there is no separate adapter class for persistence "
                    + "(see docs/adr for the framing-A-vs-B discussion)");

    /**
     * Layering: presentation (web/rest/api) → application → persistence, over the domain leaf.
     * {@code configurations} and {@code time} are cross-cutting and intentionally not modelled
     * ({@code consideringOnlyDependenciesInLayers}).
     *
     * <p>The old flat "Services" layer (a second, technically-distinct use-case layer alongside
     * "Application" — the exact duplication that motivated this whole restructuring, see the plan's
     * Context section) is gone as of the last bounded-context migration: every context now has a
     * single {@code application} package, and no package anywhere uses a {@code services} segment
     * any more. Kept only until the final cleanup step removes this whole rule (superseded by the
     * per-context isolation rules above, which are the actual boundary that matters now).
     */
    @ArchTest
    static final ArchRule layers_are_respected = layeredArchitecture().consideringOnlyDependenciesInLayers()
            .layer("Domain").definedBy("..domain..")
            .layer("Persistence").definedBy("..persistence..")
            .layer("Application").definedBy("..application..")
            .layer("Presentation").definedBy("..web..", "..rest..", "..api..")

            .whereLayer("Presentation").mayNotBeAccessedByAnyLayer()
            .whereLayer("Application").mayOnlyBeAccessedByLayers("Presentation")
            // Persistence (Spring Data repositories) is the port the use-case layer consumes.
            .whereLayer("Persistence").mayOnlyBeAccessedByLayers("Application")
            // Domain is a leaf and may be accessed by any layer — no constraint.
            .because("presentation depends only on the application layer; repositories back the "
                    + "application (use-case) layer");
}
