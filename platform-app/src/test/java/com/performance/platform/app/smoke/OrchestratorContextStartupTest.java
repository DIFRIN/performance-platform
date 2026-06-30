package com.performance.platform.app.smoke;

import com.performance.platform.PerformancePlatformApplication;
import com.performance.platform.application.ports.in.ExecuteScenarioUseCase;
import com.performance.platform.application.ports.in.GetExecutionStatusUseCase;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Smoke test : demarrage du contexte Spring en mode ORCHESTRATOR.
 *
 * <p>Utilise le profil orchestrateur mais force le transport IN_MEMORY
 * (evite Kafka) et la datasource H2 (evite PostgreSQL). Desactive
 * la securite pour simplifier le demarrage en test.
 */
@DisplayName("OrchestratorContextStartup")
class OrchestratorContextStartupTest {

    @Test
    @DisplayName("should start context in ORCHESTRATOR mode with essential beans")
    void shouldStartOrchestratorContext() {
        ConfigurableApplicationContext ctx = new SpringApplicationBuilder(
                PerformancePlatformApplication.class)
                .web(WebApplicationType.NONE)
                .run(
                        "--spring.profiles.active=orchestrator",
                        // Transport IN_MEMORY (evite Kafka)
                        "--transport.type=IN_MEMORY",
                        // Datasource H2 en memoire (evite PostgreSQL)
                        "--platform.datasources.default.url=jdbc:h2:mem:orchestrator-smoke;DB_CLOSE_DELAY=-1;MODE=PostgreSQL",
                        "--platform.datasources.default.username=sa",
                        "--platform.datasources.default.password=",
                        "--platform.datasources.default.driver-class-name=org.h2.Driver",
                        // Desactiver la securite pour le smoke test
                        "--platform.security.enabled=false"
                );

        try {
            // Engine fournissant ExecuteScenarioUseCase (RemoteExecutionEngine)
            assertThat(ctx.getBeanNamesForType(ExecuteScenarioUseCase.class))
                    .as("ExecuteScenarioUseCase bean should be present (RemoteExecutionEngine)")
                    .isNotEmpty();

            // Read-model
            assertThat(ctx.getBeanNamesForType(GetExecutionStatusUseCase.class))
                    .as("GetExecutionStatusUseCase bean should be present")
                    .isNotEmpty();
        } finally {
            ctx.close();
        }
    }
}
