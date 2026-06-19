package com.performance.platform.infrastructure.arch;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

/**
 * Test ArchUnit garantissant la separation stricte des 4 sous-packages de
 * {@code platform-infrastructure} : {@code executor}, {@code plugin},
 * {@code persistence}, {@code publisher}.
 *
 * <p>Regles verifiees :
 * <ul>
 *   <li>{@code persistence} ne depend pas de {@code executor}, {@code plugin}, {@code publisher}</li>
 *   <li>{@code publisher} ne depend pas de {@code executor}, {@code persistence}, {@code plugin}</li>
 *   <li>{@code executor} ne depend pas de {@code persistence}, {@code publisher}</li>
 *   <li>{@code plugin} peut dependre de {@code executor} mais pas de {@code persistence}/{@code publisher}</li>
 *   <li>Aucun cycle entre les 4 slices</li>
 *   <li>Annotations JPA confinees a {@code persistence}</li>
 * </ul>
 *
 * <p>Note : les packages {@code persistence} et {@code publisher} n'existent pas encore.
 * Les regles dont le {@code that()} cible ces packages utilisent {@code allowEmptyShould(true)}
 * pour eviter un echec a vide. Les regles seront automatiquement effectives des que
 * ces packages contiendront des classes.
 */
@DisplayName("Infrastructure Package Separation — 4 slices independantes")
class InfrastructurePackageSeparationTest {

    private static final String INFRA = "com.performance.platform.infrastructure";
    private static final String PKG_EXECUTOR = INFRA + ".executor..";
    private static final String PKG_PLUGIN = INFRA + ".plugin..";
    private static final String PKG_PERSISTENCE = INFRA + ".persistence..";
    private static final String PKG_PUBLISHER = INFRA + ".publisher..";

    private static JavaClasses infrastructureClasses;

    @BeforeAll
    static void loadClasses() {
        var classesPath = Path.of("target", "classes");
        infrastructureClasses = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPath(classesPath);
    }

    // ──── persistence (n'existe pas encore — allowEmptyShould) ───────

    @Test
    @DisplayName("persistence ne depend pas de executor")
    void persistenceDoesNotDependOnExecutor() {
        noClasses()
                .that().resideInAPackage(PKG_PERSISTENCE)
                .should().dependOnClassesThat().resideInAPackage(PKG_EXECUTOR)
                .because("persistence doit etre independant de executor")
                .allowEmptyShould(true)
                .check(infrastructureClasses);
    }

    @Test
    @DisplayName("persistence ne depend pas de plugin")
    void persistenceDoesNotDependOnPlugin() {
        noClasses()
                .that().resideInAPackage(PKG_PERSISTENCE)
                .should().dependOnClassesThat().resideInAPackage(PKG_PLUGIN)
                .because("persistence doit etre independant de plugin")
                .allowEmptyShould(true)
                .check(infrastructureClasses);
    }

    @Test
    @DisplayName("persistence ne depend pas de publisher")
    void persistenceDoesNotDependOnPublisher() {
        noClasses()
                .that().resideInAPackage(PKG_PERSISTENCE)
                .should().dependOnClassesThat().resideInAPackage(PKG_PUBLISHER)
                .because("persistence doit etre independant de publisher")
                .allowEmptyShould(true)
                .check(infrastructureClasses);
    }

    // ──── publisher (n'existe pas encore — allowEmptyShould) ─────────

    @Test
    @DisplayName("publisher ne depend pas de executor")
    void publisherDoesNotDependOnExecutor() {
        noClasses()
                .that().resideInAPackage(PKG_PUBLISHER)
                .should().dependOnClassesThat().resideInAPackage(PKG_EXECUTOR)
                .because("publisher doit etre independant de executor")
                .allowEmptyShould(true)
                .check(infrastructureClasses);
    }

    @Test
    @DisplayName("publisher ne depend pas de persistence")
    void publisherDoesNotDependOnPersistence() {
        noClasses()
                .that().resideInAPackage(PKG_PUBLISHER)
                .should().dependOnClassesThat().resideInAPackage(PKG_PERSISTENCE)
                .because("publisher doit etre independant de persistence")
                .allowEmptyShould(true)
                .check(infrastructureClasses);
    }

    @Test
    @DisplayName("publisher ne depend pas de plugin")
    void publisherDoesNotDependOnPlugin() {
        noClasses()
                .that().resideInAPackage(PKG_PUBLISHER)
                .should().dependOnClassesThat().resideInAPackage(PKG_PLUGIN)
                .because("publisher doit etre independant de plugin")
                .allowEmptyShould(true)
                .check(infrastructureClasses);
    }

    // ──── executor (package existant — verification active) ──────────

    @Test
    @DisplayName("executor ne depend pas de persistence")
    void executorDoesNotDependOnPersistence() {
        noClasses()
                .that().resideInAPackage(PKG_EXECUTOR)
                .should().dependOnClassesThat().resideInAPackage(PKG_PERSISTENCE)
                .because("executor ne doit pas dependre de persistence")
                .check(infrastructureClasses);
    }

    @Test
    @DisplayName("executor ne depend pas de publisher")
    void executorDoesNotDependOnPublisher() {
        noClasses()
                .that().resideInAPackage(PKG_EXECUTOR)
                .should().dependOnClassesThat().resideInAPackage(PKG_PUBLISHER)
                .because("executor ne doit pas dependre de publisher")
                .check(infrastructureClasses);
    }

    // ──── plugin (package existant — verification active) ────────────

    @Test
    @DisplayName("plugin ne depend pas de persistence")
    void pluginDoesNotDependOnPersistence() {
        noClasses()
                .that().resideInAPackage(PKG_PLUGIN)
                .should().dependOnClassesThat().resideInAPackage(PKG_PERSISTENCE)
                .because("plugin ne doit pas dependre de persistence")
                .check(infrastructureClasses);
    }

    @Test
    @DisplayName("plugin ne depend pas de publisher")
    void pluginDoesNotDependOnPublisher() {
        noClasses()
                .that().resideInAPackage(PKG_PLUGIN)
                .should().dependOnClassesThat().resideInAPackage(PKG_PUBLISHER)
                .because("plugin ne doit pas dependre de publisher")
                .check(infrastructureClasses);
    }

    // ──── cycles (verification active sur tous les packages) ─────────

    @Test
    @DisplayName("aucun cycle entre les 4 slices infrastructure")
    void noCyclesBetweenSlices() {
        slices()
                .matching(INFRA + ".(*)..")
                .should().beFreeOfCycles()
                .check(infrastructureClasses);
    }

    // ──── JPA (verification active sur tous les packages) ────────────

    @Test
    @DisplayName("annotations JPA confinees au package persistence")
    void jpaAnnotationsConfinedToPersistence() {
        noClasses()
                .that().resideOutsideOfPackage(PKG_PERSISTENCE)
                .should().dependOnClassesThat().resideInAPackage("jakarta.persistence..")
                .because("les annotations JPA (jakarta.persistence) doivent etre confinees a persistence")
                .check(infrastructureClasses);
    }
}
