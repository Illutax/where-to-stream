package tech.dobler.where2stream.architecture;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Date;

import static com.tngtech.archunit.base.DescribedPredicate.not;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAPackage;
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
     * depend on it only through its published port ({@code CurrentUserPort}), never through its
     * domain/application/adapter internals directly. One such rule gets added per migrated
     * context. {@code shared..} is exempt: {@code ApiExceptionHandler} deliberately maps every
     * context's own exception types (a cross-cutting concern the shared kernel is meant to know
     * about), which is a different thing from one bounded context depending on another's internals.
     */
    @ArchTest
    static final ArchRule accountaccess_is_only_accessed_through_its_published_ports = noClasses()
            .that().resideOutsideOfPackage("..accountaccess..")
            .and().resideOutsideOfPackage("..shared..")
            .should().dependOnClassesThat(
                    resideInAPackage("..accountaccess..")
                            .and(not(resideInAPackage("..accountaccess.application.port..")))
            )
            .because("other bounded contexts may depend on accountaccess only through its "
                    + "published ports (e.g. CurrentUserPort), not its internals");

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
