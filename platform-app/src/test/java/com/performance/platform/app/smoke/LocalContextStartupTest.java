package com.performance.platform.app.smoke;

import com.performance.platform.PerformancePlatformApplication;
import com.performance.platform.application.ports.in.ExecuteScenarioUseCase;
import com.performance.platform.application.ports.in.GetExecutionStatusUseCase;
import com.performance.platform.engine.local.TaskExecutorLookup;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Smoke test : demarrage du contexte Spring en mode LOCAL.
 *
 * <p>Utilise le profil {@code local} (H2 in-memory, transport IN_MEMORY).
 * Verifie la presence des beans attendus sans Testcontainers ni @SpringBootTest.
 */
@DisplayName("LocalContextStartup")
class LocalContextStartupTest {

    @Test
    @DisplayName("should start context in LOCAL mode with essential beans")
    void shouldStartLocalContext() {
        ConfigurableApplicationContext ctx = new SpringApplicationBuilder(
                PerformancePlatformApplication.class)
                .web(WebApplicationType.NONE)
                .run(
                        "--spring.profiles.active=local",
                        "--transport.type=IN_MEMORY",
                        "--platform.security.enabled=false"
                );

        try {
            // Engine fournissant ExecuteScenarioUseCase (LocalExecutionEngine)
            assertThat(ctx.getBeanNamesForType(ExecuteScenarioUseCase.class))
                    .as("ExecuteScenarioUseCase bean should be present (LocalExecutionEngine)")
                    .isNotEmpty();

            // Read-model (ISSUE-146)
            assertThat(ctx.getBeanNamesForType(GetExecutionStatusUseCase.class))
                    .as("GetExecutionStatusUseCase bean should be present")
                    .isNotEmpty();

            // Glue cross-module (RegistryTaskExecutorLookup)
            assertThat(ctx.getBeanNamesForType(TaskExecutorLookup.class))
                    .as("TaskExecutorLookup bean should be present")
                    .isNotEmpty();
        } finally {
            ctx.close();
        }
    }
}
