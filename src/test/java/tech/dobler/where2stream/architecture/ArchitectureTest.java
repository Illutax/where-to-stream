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
     * A Spring Data repository interface is itself the outbound port to the database: Spring Data
     * generates the adapter (a runtime proxy) directly from the interface, so there's no separate
     * hand-written adapter class the way there is for e.g. {@code PosterSource}. Scoped to
     * already-migrated contexts for now — old flat {@code persistence} classes haven't moved yet;
     * extend the package list as each further context migrates (finished in the final cleanup step).
     */
    @ArchTest
    static final ArchRule spring_data_repositories_are_the_port_not_the_adapter = classes()
            .that().areAssignableTo(Repository.class)
            .and().resideInAnyPackage("..accountaccess..", "..watchlist..")
            .should().resideInAPackage("..port.out..")
            .because("the repository interface is the outbound port; JPA supplies the adapter as a "
                    + "runtime proxy, so there is no separate adapter class for persistence "
                    + "(see docs/adr for the framing-A-vs-B discussion)");

    /**
     * Layering: presentation (web/rest/api) → application → services → persistence, over the
     * domain leaf. {@code configurations} and {@code time} are cross-cutting and intentionally
     * not modelled ({@code consideringOnlyDependenciesInLayers}).
     */
    @ArchTest
    static final ArchRule layers_are_respected = layeredArchitecture().consideringOnlyDependenciesInLayers()
            .layer("Domain").definedBy("..domain..")
            .layer("Persistence").definedBy("..persistence..")
            .layer("Services").definedBy("..services..")
            .layer("Application").definedBy("..application..")
            .layer("Presentation").definedBy("..web..", "..rest..", "..api..")

            .whereLayer("Presentation").mayNotBeAccessedByAnyLayer()
            .whereLayer("Application").mayOnlyBeAccessedByLayers("Presentation")
            .whereLayer("Services").mayOnlyBeAccessedByLayers("Application")
            // Persistence (Spring Data repositories) is the port the use-case layer consumes, so
            // both Services and the Application layer may access it (e.g. UserAdminService).
            .whereLayer("Persistence").mayOnlyBeAccessedByLayers("Services", "Application")
            // Domain is a leaf and may be accessed by any layer — no constraint.
            .because("presentation depends only on the application layer; repositories back the "
                    + "services and application (use-case) layers");
}
