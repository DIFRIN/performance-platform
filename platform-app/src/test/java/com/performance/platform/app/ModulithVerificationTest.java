package com.performance.platform.app;

import com.performance.platform.PerformancePlatformApplication;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifie la detection des modules Spring Modulith.
 * <p>
 * Note: La detection des modules est limitee aux packages contenant
 * des classes Spring-stereotypees. Les modules purement domaine
 * (platform-domain : 0 annotation Spring) ne sont pas detectes
 * par le scan de composants Spring Modulith.
 * <p>
 * La verification complete des violations inter-modules se fera
 * dans les tests E2E (ISSUE-082) avec tous les profils actifs.
 */
@DisplayName("ModulithVerification")
class ModulithVerificationTest {

    @Test
    @DisplayName("should detect Spring-managed modules")
    void shouldDetectSpringManagedModules() {
        var modules = ApplicationModules.of(PerformancePlatformApplication.class);

        assertThat(modules).isNotNull();

        var moduleNames = modules.stream()
                .map(m -> m.getName())
                .toList();

        assertThat(moduleNames).isNotEmpty();

        // Verifie la presence d'au moins un module infra/persistance
        assertThat(moduleNames)
                .anyMatch(n -> n.contains("infrastructure") || n.contains("app"));
    }
}
