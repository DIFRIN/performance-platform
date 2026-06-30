package com.performance.platform.agent.registry.config;

import com.performance.platform.agent.registry.AgentRegistry;
import com.performance.platform.agent.registry.InMemoryAgentRegistry;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests de la {@link AgentRegistryConfiguration}.
 * <p>
 * Verifie que le bean {@link AgentRegistry} est cree uniquement
 * en mode DISTRIBUTED + role ORCHESTRATOR, et absent en LOCAL et AGENT.
 */
@DisplayName("AgentRegistryConfiguration")
class AgentRegistryConfigurationTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(AgentRegistryConfiguration.class);

    // ========================================================================
    // ORCHESTRATOR (le bean est cree)
    // ========================================================================

    @Nested
    @DisplayName("DISTRIBUTED + ORCHESTRATOR")
    class OrchestratorModeTests {

        @Test
        @DisplayName("should create AgentRegistry bean")
        void shouldCreateAgentRegistryBean() {
            runner.withPropertyValues(
                    "runtime.mode=DISTRIBUTED",
                    "runtime.role=ORCHESTRATOR"
            ).run(ctx -> {
                assertThat(ctx.containsBean("agentRegistry")).isTrue();
                assertThat(ctx.getBean(AgentRegistry.class))
                        .isInstanceOf(InMemoryAgentRegistry.class);
            });
        }

        @Test
        @DisplayName("should have a single bean of type AgentRegistry")
        void shouldHaveSingleAgentRegistryBean() {
            runner.withPropertyValues(
                    "runtime.mode=DISTRIBUTED",
                    "runtime.role=ORCHESTRATOR"
            ).run(ctx -> {
                assertThat(ctx).hasSingleBean(AgentRegistry.class);
            });
        }
    }

    // ========================================================================
    // LOCAL (pas de bean)
    // ========================================================================

    @Nested
    @DisplayName("LOCAL (default role)")
    class LocalModeTests {

        @Test
        @DisplayName("should not create AgentRegistry bean in LOCAL mode")
        void shouldNotCreateAgentRegistryInLocalMode() {
            runner.withPropertyValues("runtime.mode=LOCAL")
                    .run(ctx -> {
                        assertThat(ctx.containsBean("agentRegistry")).isFalse();
                    });
        }

        @Test
        @DisplayName("should not have any AgentRegistry bean in LOCAL mode")
        void shouldNotHaveAgentRegistryBeanInLocalMode() {
            runner.withPropertyValues("runtime.mode=LOCAL")
                    .run(ctx -> {
                        assertThat(ctx.getBeanProvider(AgentRegistry.class).getIfAvailable())
                                .isNull();
                    });
        }
    }

    // ========================================================================
    // AGENT (pas de bean)
    // ========================================================================

    @Nested
    @DisplayName("DISTRIBUTED + AGENT")
    class AgentModeTests {

        @Test
        @DisplayName("should not create AgentRegistry bean in AGENT mode")
        void shouldNotCreateAgentRegistryInAgentMode() {
            runner.withPropertyValues(
                    "runtime.mode=DISTRIBUTED",
                    "runtime.role=AGENT"
            ).run(ctx -> {
                assertThat(ctx.containsBean("agentRegistry")).isFalse();
            });
        }

        @Test
        @DisplayName("should not have any AgentRegistry bean in AGENT mode")
        void shouldNotHaveAgentRegistryBeanInAgentMode() {
            runner.withPropertyValues(
                    "runtime.mode=DISTRIBUTED",
                    "runtime.role=AGENT"
            ).run(ctx -> {
                assertThat(ctx.getBeanProvider(AgentRegistry.class).getIfAvailable())
                        .isNull();
            });
        }
    }

    // ========================================================================
    // Par defaut (LOCAL, sans role explicite)
    // ========================================================================

    @Nested
    @DisplayName("Default (no properties)")
    class DefaultModeTests {

        @Test
        @DisplayName("should not create AgentRegistry bean by default")
        void shouldNotCreateAgentRegistryByDefault() {
            runner.run(ctx -> {
                assertThat(ctx.containsBean("agentRegistry")).isFalse();
            });
        }
    }

    // ========================================================================
    // DISTRIBUTED sans role (pas de bean)
    // ========================================================================

    @Nested
    @DisplayName("DISTRIBUTED without role")
    class DistributedWithoutRoleTests {

        @Test
        @DisplayName("should not create AgentRegistry bean when role is not set")
        void shouldNotCreateAgentRegistryWithoutRole() {
            runner.withPropertyValues("runtime.mode=DISTRIBUTED")
                    .run(ctx -> {
                        assertThat(ctx.containsBean("agentRegistry")).isFalse();
                    });
        }
    }
}
