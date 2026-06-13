package com.performance.platform.plugin;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * Test ArchUnit garantissant que {@code platform-plugin-api} n'a aucune dependance
 * vers Spring. Les interfaces et annotations du plugin API doivent etre utilisables
 * dans des JARs externes sans imposer Spring sur leur classpath.
 *
 * <p>Regle architecturale non negociable : 0 import org.springframework dans
 * {@code com.performance.platform.plugin..}.</p>
 */
@DisplayName("Plugin API Architecture — 0 dependance Spring")
class PluginApiArchitectureTest {

    private static final String PLUGIN_PACKAGE = "com.performance.platform.plugin..";

    private static JavaClasses pluginClasses;

    @BeforeAll
    static void loadClasses() {
        var classesPath = Path.of("target", "classes");
        pluginClasses = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPath(classesPath);
    }

    @Test
    @DisplayName("aucune classe du plugin API ne depend de Spring")
    void noSpringDependency() {
        noClasses()
                .that().resideInAPackage(PLUGIN_PACKAGE)
                .should().dependOnClassesThat().resideInAPackage("org.springframework..")
                .because("platform-plugin-api doit etre 0 dependance Spring pour les plugins externes")
                .allowEmptyShould(true)
                .check(pluginClasses);
    }
}
