package tech.dobler.werstreamt.architecture;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Date;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.Architectures.layeredArchitecture;

/**
 * Enforced architecture rules. Analyses production classes only (tests may read the real clock
 * and construct fixtures freely).
 */
@AnalyzeClasses(packages = "tech.dobler.werstreamt", importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureTest {

    /**
     * Time must be read through the {@code TimeService} facade, never via static {@code now()}
     * calls (ADR-0003). The only exception is the facade's production implementation.
     */
    @ArchTest
    static final ArchRule time_is_read_only_through_the_facade = noClasses()
            .that().doNotHaveFullyQualifiedName("tech.dobler.werstreamt.time.SystemTimeService")
            .should().callMethod(Instant.class, "now")
            .orShould().callMethod(LocalDate.class, "now")
            .orShould().callMethod(LocalDateTime.class, "now")
            .orShould().callMethod(System.class, "currentTimeMillis")
            .orShould().callConstructor(Date.class)
            .because("time must be read through TimeService, not static now() calls (ADR-0003)");

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
            .whereLayer("Persistence").mayOnlyBeAccessedByLayers("Services")
            // Domain is a leaf and may be accessed by any layer — no constraint.
            .because("presentation should depend only on the application layer (plus the domain)");
}
