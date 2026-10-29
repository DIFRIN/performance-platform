package com.performance.platform.app.smoke;

import com.performance.platform.PerformancePlatformApplication;
import com.performance.platform.agent.runtime.AgentRuntime;

import jakarta.persistence.EntityManagerFactory;

import javax.sql.DataSource;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Smoke test : demarrage du contexte Spring en mode AGENT.
 *
 * <p>Verifie qu'en mode AGENT :
 * <ul>
 *   <li>Le serveur web est desactive ({@code WebApplicationType.NONE})</li>
 *   <li>Aucune datasource JPA n'est creee (ni {@link DataSource}, ni
 *       {@link EntityManagerFactory})</li>
 *   <li>Le {@link AgentRuntime} ({@code DistributedAgentRuntime}) est present</li>
 * </ul>
 *
 * <p>Utilise le profil agent mais force le transport IN_MEMORY (evite Kafka).
 * Desactive la securite pour simplifier le demarrage en test.
 */
@DisplayName("AgentContextStartup")
class AgentContextStartupTest {

    @Test
    @DisplayName("should start AGENT context without datasource and with AgentRuntime")
    void shouldStartAgentContext() {
        ConfigurableApplicationContext ctx = new SpringApplicationBuilder(
                PerformancePlatformApplication.class)
                .web(WebApplicationType.NONE)
                .run(
                        "--spring.profiles.active=agent",
                        // Transport IN_MEMORY (evite Kafka)
                        "--transport.type=IN_MEMORY",
                        // Au moins une task supportee pour eviter le warning idle
                        "--agent.supported-tasks=mock-server",
                        // Desactiver la securite pour le smoke test
                        "--platform.security.enabled=false"
                );

        try {
            // AgentRuntime present (DistributedAgentRuntime)
            assertThat(ctx.getBeanNamesForType(AgentRuntime.class))
                    .as("AgentRuntime bean should be present (DistributedAgentRuntime)")
                    .isNotEmpty();

            // Aucune datasource JPA en mode AGENT (ISSUE-145)
            assertThat(ctx.getBeanNamesForType(DataSource.class))
                    .as("DataSource bean should NOT be present in AGENT mode")
                    .isEmpty();

            assertThat(ctx.getBeanNamesForType(EntityManagerFactory.class))
                    .as("EntityManagerFactory bean should NOT be present in AGENT mode")
                    .isEmpty();
        } finally {
            ctx.close();
        }
    }
}
