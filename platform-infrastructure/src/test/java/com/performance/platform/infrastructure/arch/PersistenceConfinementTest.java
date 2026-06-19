package com.performance.platform.infrastructure.arch;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import jakarta.persistence.Entity;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * Test ArchUnit garantissant le confinement strict des annotations et entites
 * JPA dans le sous-package {@code .infrastructure.persistence}.
 *
 * <p>Regles verifiees :
 * <ul>
 *   <li>Les annotations {@code jakarta.persistence.*} ne sont utilisees que dans
 *       {@code .infrastructure.persistence..}</li>
 *   <li>Les classes annotees {@code @Entity} ne sont jamais referencees
 *       hors du package {@code .persistence..} (les adapters retournent
 *       exclusivement des records domaine)</li>
 *   <li>Les packages {@code executor}, {@code plugin}, et {@code publisher}
 *       ne dependent pas de {@code persistence}</li>
 * </ul>
 *
 * <p>Ce test est specifique a la couche persistence et vient completer
 * {@link InfrastructurePackageSeparationTest} qui couvre la separation
 * generale des 4 slices infrastructure.</p>
 */
@DisplayName("Persistence Confinement — JPA confined to persistence package")
class PersistenceConfinementTest {

    private static final String INFRA = "com.performance.platform.infrastructure";
    private static final String PKG_PERSISTENCE = INFRA + ".persistence..";
    private static final String PKG_EXECUTOR = INFRA + ".executor..";
    private static final String PKG_PLUGIN = INFRA + ".plugin..";
    private static final String PKG_PUBLISHER = INFRA + ".publisher..";

    private static JavaClasses infrastructureClasses;

    @BeforeAll
    static void loadClasses() {
        var classesPath = Path.of("target", "classes");
        infrastructureClasses = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPath(classesPath);
    }

    // ──── JPA annotations confined to persistence ──────────────────────

    @Test
    @DisplayName("jakarta.persistence annotations only in persistence package")
    void jpaAnnotationsConfinedToPersistence() {
        noClasses()
                .that().resideOutsideOfPackage(PKG_PERSISTENCE)
                .should().dependOnClassesThat().resideInAPackage("jakarta.persistence..")
                .because("les annotations JPA (jakarta.persistence) doivent etre confinees au package persistence")
                .check(infrastructureClasses);
    }

    // ──── @Entity classes not referenced outside persistence ───────────

    /**
     * Predicate identifying JPA entity classes — classes directly annotated
     * with {@code @Entity}. Matches {@link jakarta.persistence.Entity}
     * directly, not meta-annotations.
     */
    private static final DescribedPredicate<JavaClass> ARE_ENTITIES =
            new DescribedPredicate<>("are JPA @Entity") {
                @Override
                public boolean test(JavaClass javaClass) {
                    return javaClass.isAnnotatedWith(Entity.class);
                }
            };

    @Test
    @DisplayName("@Entity classes never referenced outside persistence")
    void entitiesNotReferencedOutsidePersistence() {
        noClasses()
                .that().resideOutsideOfPackage(PKG_PERSISTENCE)
                .should().dependOnClassesThat(ARE_ENTITIES)
                .because("les entites JPA (@Entity) ne doivent pas etre referencees hors du package persistence ; les adapters retournent des records domaine")
                .check(infrastructureClasses);
    }

    // ──── executor does not depend on persistence ──────────────────────

    @Test
    @DisplayName("executor ne depend pas de persistence")
    void executorDoesNotDependOnPersistence() {
        noClasses()
                .that().resideInAPackage(PKG_EXECUTOR)
                .should().dependOnClassesThat().resideInAPackage(PKG_PERSISTENCE)
                .because("executor ne doit pas dependre du package persistence")
                .check(infrastructureClasses);
    }

    // ──── plugin does not depend on persistence ────────────────────────

    @Test
    @DisplayName("plugin ne depend pas de persistence")
    void pluginDoesNotDependOnPersistence() {
        noClasses()
                .that().resideInAPackage(PKG_PLUGIN)
                .should().dependOnClassesThat().resideInAPackage(PKG_PERSISTENCE)
                .because("plugin ne doit pas dependre du package persistence")
                .check(infrastructureClasses);
    }

    // ──── publisher does not depend on persistence (allowEmptyShould) ──

    @Test
    @DisplayName("publisher ne depend pas de persistence")
    void publisherDoesNotDependOnPersistence() {
        noClasses()
                .that().resideInAPackage(PKG_PUBLISHER)
                .should().dependOnClassesThat().resideInAPackage(PKG_PERSISTENCE)
                .because("publisher ne doit pas dependre du package persistence")
                .allowEmptyShould(true)
                .check(infrastructureClasses);
    }
}
