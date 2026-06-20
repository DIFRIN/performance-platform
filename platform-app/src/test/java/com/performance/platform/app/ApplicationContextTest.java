package com.performance.platform.app;

import com.performance.platform.PerformancePlatformApplication;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.modulith.Modulith;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifie les metadonnees de la classe principale.
 * <p>
 * Note: Le demarrage effectif du contexte Spring Boot 4.0.0
 * sera verifie en integration (ISSUE-082) quand les profils
 * de configuration seront finalises.
 */
@DisplayName("ApplicationContext")
class ApplicationContextTest {

    @Test
    @DisplayName("should have @SpringBootApplication annotation")
    void shouldHaveSpringBootApplicationAnnotation() {
        var annotation = PerformancePlatformApplication.class
                .getAnnotation(SpringBootApplication.class);
        assertThat(annotation).isNotNull();
    }

    @Test
    @DisplayName("should have @Modulith annotation")
    void shouldHaveModulithAnnotation() {
        var annotation = PerformancePlatformApplication.class
                .getAnnotation(Modulith.class);
        assertThat(annotation).isNotNull();
    }

    @Test
    @DisplayName("should have main method")
    void shouldHaveMainMethod() throws NoSuchMethodException {
        var method = PerformancePlatformApplication.class
                .getMethod("main", String[].class);
        assertThat(method).isNotNull();
    }
}
