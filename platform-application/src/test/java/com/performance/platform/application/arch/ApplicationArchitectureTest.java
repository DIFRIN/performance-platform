package com.performance.platform.application.arch;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * Test ArchUnit garantissant que {@code platform-application} n'a aucune dependance
 * vers Spring ni vers les adapteurs infrastructure.
 *
 * Les ports et exceptions du module application sont des interfaces pures
 * et des classes simples — 0 annotation framework.
 */
@DisplayName("Application Architecture — 0 Spring, 0 infrastructure")
class ApplicationArchitectureTest {

    private static final String APPLICATION_PACKAGE = "com.performance.platform.application..";

    private static JavaClasses applicationClasses;

    @BeforeAll
    static void loadClasses() {
        var classesPath = Path.of("target", "classes");
        applicationClasses = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPath(classesPath);
    }

    @Test
    @DisplayName("aucune classe du module application ne depend de Spring")
    void noSpringDependency() {
        noClasses()
                .that().resideInAPackage(APPLICATION_PACKAGE)
                .should().dependOnClassesThat().resideInAPackage("org.springframework..")
                .because("platform-application — les ports et exceptions sont 0 annotation Spring")
                .allowEmptyShould(true)
                .check(applicationClasses);
    }

    @Test
    @DisplayName("aucune classe du module application ne depend de l'infrastructure")
    void noInfrastructureDependency() {
        noClasses()
                .that().resideInAPackage(APPLICATION_PACKAGE)
                .should().dependOnClassesThat().resideInAPackage("com.performance.platform.infrastructure..")
                .because("platform-application — les ports ne doivent pas dependre des adapteurs")
                .allowEmptyShould(true)
                .check(applicationClasses);
    }
}
