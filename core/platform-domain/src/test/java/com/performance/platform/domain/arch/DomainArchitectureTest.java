package com.performance.platform.domain.arch;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * Test ArchUnit garantissant que {@code platform-domain} n'a aucune dependance
 * vers Spring, Jakarta Persistence (JPA), ou Jackson.
 *
 * 0 annotation framework dans le domaine — regle architecturale non negociable.
 */
@DisplayName("Domain Architecture — 0 dependance framework")
class DomainArchitectureTest {

    private static final String DOMAIN_PACKAGE = "com.performance.platform.domain..";

    private static JavaClasses domainClasses;

    @BeforeAll
    static void loadClasses() {
        var classesPath = Path.of("target", "classes");
        domainClasses = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPath(classesPath);
    }

    @Test
    @DisplayName("aucune classe du domaine ne depend de Spring")
    void noSpringDependency() {
        noClasses()
            .that().resideInAPackage(DOMAIN_PACKAGE)
            .should().dependOnClassesThat().resideInAPackage("org.springframework..")
            .because("platform-domain doit etre 0 annotation Spring")
            .allowEmptyShould(true)
            .check(domainClasses);
    }

    @Test
    @DisplayName("aucune classe du domaine ne depend de Jakarta Persistence (JPA)")
    void noJpaDependency() {
        noClasses()
            .that().resideInAPackage(DOMAIN_PACKAGE)
            .should().dependOnClassesThat().resideInAPackage("jakarta.persistence..")
            .because("platform-domain ne doit pas contenir d'entite JPA")
            .allowEmptyShould(true)
            .check(domainClasses);
    }

    @Test
    @DisplayName("aucune classe du domaine ne depend de Jackson")
    void noJacksonDependency() {
        noClasses()
            .that().resideInAPackage(DOMAIN_PACKAGE)
            .should().dependOnClassesThat().resideInAPackage("com.fasterxml.jackson..")
            .because("platform-domain ne doit pas contenir d'annotation Jackson")
            .allowEmptyShould(true)
            .check(domainClasses);
    }
}
