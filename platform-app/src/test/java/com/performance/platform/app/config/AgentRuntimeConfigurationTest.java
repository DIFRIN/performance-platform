package com.performance.platform.app.config;

import com.performance.platform.agent.filter.TaskSpecializationFilter;
import com.performance.platform.agent.local.LocalAgent;
import com.performance.platform.agent.registration.AgentRegistrationPort;
import com.performance.platform.agent.runtime.AgentRuntime;
import com.performance.platform.agent.runtime.DistributedAgentRuntime;
import com.performance.platform.domain.execution.ExecutionContext;
import com.performance.platform.domain.id.TaskId;
import com.performance.platform.domain.scenario.StepDefinition;
import com.performance.platform.domain.task.TaskResult;
import com.performance.platform.plugin.TaskExecutor;
import com.performance.platform.transport.ExecutionTransport;
import com.performance.platform.transport.inmemory.InMemoryExecutionTransport;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests de la {@link AgentRuntimeConfiguration}.
 * <p>
 * Verifie que les bons beans sont crees selon {@code runtime.mode}
 * et {@code runtime.role}, via {@link ApplicationContextRunner}
 * (tests legers sans demarrer l'application entiere).
 */
@DisplayName("AgentRuntimeConfiguration")
class AgentRuntimeConfigurationTest {

    /**
     * TaskExecutor factice pour les tests. Retourne un nom de tache fixe.
     */
    private static TaskExecutor stubExecutor(String taskName) {
        return new TaskExecutor() {
            @Override
            public TaskResult execute(ExecutionContext context, StepDefinition step) {
                return TaskResult.success(
                        TaskId.of("stub-" + taskName), taskName,
                        java.time.Duration.ZERO, java.util.Map.of());
            }

            @Override
            public String getSupportedTaskName() {
                return taskName;
            }
        };
    }

    /**
     * Configuration de test qui fournit les beans de dependance
     * (mocks ou stubs) necessaires a AgentRuntimeConfiguration.
     */
    @Configuration
    static class TestDependencyConfig {

        @Bean
        InMemoryExecutionTransport inMemoryExecutionTransport() {
            return new InMemoryExecutionTransport();
        }

        @Bean
        List<TaskExecutor> taskExecutors() {
            return List.of(
                    stubExecutor("mock-server"),
                    stubExecutor("http-client")
            );
        }
    }

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(
                    AgentRuntimeConfiguration.class,
                    TestDependencyConfig.class
            );

    // ========================================================================
    // Mode LOCAL
    // ========================================================================

    @Nested
    @DisplayName("Mode LOCAL")
    class LocalModeTests {

        @Test
        @DisplayName("should create LocalAgent bean")
        void shouldCreateLocalAgent() {
            runner.withPropertyValues("runtime.mode=LOCAL")
                    .run(ctx -> {
                        assertThat(ctx.containsBean("localAgentRuntime")).isTrue();
                        assertThat(ctx.containsBean("distributedAgentRuntime")).isFalse();
                        assertThat(ctx.containsBean("agentRegistrationPort")).isFalse();

                        var runtime = ctx.getBean(AgentRuntime.class);
                        assertThat(runtime).isInstanceOf(LocalAgent.class);
                    });
        }

        @Test
        @DisplayName("LocalAgent should support all task names from registered executors")
        void localAgentShouldSupportAllTaskNames() {
            runner.withPropertyValues("runtime.mode=LOCAL")
                    .run(ctx -> {
                        var runtime = ctx.getBean(AgentRuntime.class);
                        assertThat(runtime.canExecute("mock-server")).isTrue();
                        assertThat(runtime.canExecute("http-client")).isTrue();
                        // task non enregistree -> false
                        assertThat(runtime.canExecute("unknown-task")).isFalse();
                    });
        }

        @Test
        @DisplayName("should have all task names from registry in descriptor")
        void localAgentShouldHaveAllTaskNamesFromRegistry() {
            runner.withPropertyValues("runtime.mode=LOCAL")
                    .run(ctx -> {
                        assertThat(ctx).hasSingleBean(AgentRuntime.class);
                        var agent = ctx.getBean(AgentRuntime.class);
                        assertThat(agent).isInstanceOf(LocalAgent.class);
                        var taskNames = agent.getDescriptor().supportedTaskNames();
                        assertThat(taskNames).containsExactlyInAnyOrder("mock-server", "http-client");
                    });
        }

        @Test
        @DisplayName("should not create distributed beans in LOCAL mode")
        void shouldNotCreateDistributedBeans() {
            runner.withPropertyValues("runtime.mode=LOCAL")
                    .run(ctx -> {
                        assertThat(ctx.containsBean("distributedAgentRuntime")).isFalse();
                        assertThat(ctx.containsBean("taskSpecializationFilter")).isFalse();
                        assertThat(ctx.containsBean("agentRegistrationPort")).isFalse();
                    });
        }
    }

    // ========================================================================
    // Mode DISTRIBUTED + role AGENT
    // ========================================================================

    @Nested
    @DisplayName("Mode DISTRIBUTED, role AGENT")
    class DistributedAgentModeTests {

        @Test
        @DisplayName("should create DistributedAgentRuntime bean")
        void shouldCreateDistributedAgentRuntime() {
            runner.withPropertyValues(
                    "runtime.mode=DISTRIBUTED",
                    "runtime.role=AGENT"
            ).run(ctx -> {
                assertThat(ctx.containsBean("distributedAgentRuntime")).isTrue();
                assertThat(ctx.containsBean("localAgentRuntime")).isFalse();

                var runtime = ctx.getBean(AgentRuntime.class);
                assertThat(runtime).isInstanceOf(DistributedAgentRuntime.class);
            });
        }

        @Test
        @DisplayName("should create AgentRegistrationPort bean")
        void shouldCreateAgentRegistrationPort() {
            runner.withPropertyValues(
                    "runtime.mode=DISTRIBUTED",
                    "runtime.role=AGENT"
            ).run(ctx -> {
                assertThat(ctx.containsBean("agentRegistrationPort")).isTrue();
                var port = ctx.getBean(AgentRegistrationPort.class);
                assertThat(port).isNotNull();
            });
        }

        @Test
        @DisplayName("should create TaskSpecializationFilter bean")
        void shouldCreateTaskSpecializationFilter() {
            runner.withPropertyValues(
                    "runtime.mode=DISTRIBUTED",
                    "runtime.role=AGENT"
            ).run(ctx -> {
                assertThat(ctx.containsBean("taskSpecializationFilter")).isTrue();
                var filter = ctx.getBean(TaskSpecializationFilter.class);
                assertThat(filter).isNotNull();
            });
        }

        @Test
        @DisplayName("DistributedAgentRuntime with agent.supported-tasks should support configured tasks")
        void distributedAgentShouldSupportConfiguredTasks() {
            runner.withPropertyValues(
                    "runtime.mode=DISTRIBUTED",
                    "runtime.role=AGENT",
                    "agent.supported-tasks=mock-server"
            ).run(ctx -> {
                var runtime = ctx.getBean(AgentRuntime.class);
                assertThat(runtime.canExecute("mock-server")).isTrue();
                assertThat(runtime.canExecute("http-client")).isFalse();
            });
        }

        @Test
        @DisplayName("should not create LOCAL beans in DISTRIBUTED+AGENT mode")
        void shouldNotCreateLocalBeans() {
            runner.withPropertyValues(
                    "runtime.mode=DISTRIBUTED",
                    "runtime.role=AGENT"
            ).run(ctx -> {
                assertThat(ctx.containsBean("localAgentRuntime")).isFalse();
            });
        }
    }

    // ========================================================================
    // Mode ORCHESTRATOR (pas d'AgentRuntime)
    // ========================================================================

    @Nested
    @DisplayName("Mode ORCHESTRATOR")
    class OrchestratorModeTests {

        @Test
        @DisplayName("should not create any AgentRuntime bean")
        void shouldNotCreateAgentRuntime() {
            runner.withPropertyValues(
                    "runtime.mode=DISTRIBUTED",
                    "runtime.role=ORCHESTRATOR"
            ).run(ctx -> {
                assertThat(ctx.containsBean("localAgentRuntime")).isFalse();
                assertThat(ctx.containsBean("distributedAgentRuntime")).isFalse();
                assertThat(ctx.containsBean("agentRegistrationPort")).isFalse();
                assertThat(ctx.containsBean("taskSpecializationFilter")).isFalse();
                assertThat(ctx.getBeanProvider(AgentRuntime.class).getIfAvailable()).isNull();
            });
        }
    }

    // ========================================================================
    // Empty supported-tasks (agent idle)
    // ========================================================================

    @Nested
    @DisplayName("Empty supported-tasks")
    class EmptySupportedTasksTests {

        @Test
        @DisplayName("should create DistributedAgentRuntime with empty supported tasks when not configured")
        void shouldCreateAgentWithEmptySupportedTasks() {
            runner.withPropertyValues(
                    "runtime.mode=DISTRIBUTED",
                    "runtime.role=AGENT"
            ).run(ctx -> {
                // Pas de property agent.supported-tasks → liste vide
                var runtime = ctx.getBean(AgentRuntime.class);
                assertThat(runtime).isInstanceOf(DistributedAgentRuntime.class);
                // L'agent est cree mais ne supporte aucune tache
                assertThat(runtime.canExecute("mock-server")).isFalse();
                assertThat(runtime.canExecute("http-client")).isFalse();
            });
        }

        @Test
        @DisplayName("should create agent with empty supported tasks when agent.supported-tasks is blank")
        void shouldCreateAgentWithBlankSupportedTasks() {
            runner.withPropertyValues(
                    "runtime.mode=DISTRIBUTED",
                    "runtime.role=AGENT",
                    "agent.supported-tasks="
            ).run(ctx -> {
                var runtime = ctx.getBean(AgentRuntime.class);
                assertThat(runtime).isInstanceOf(DistributedAgentRuntime.class);
                assertThat(runtime.canExecute("any-task")).isFalse();
            });
        }
    }

    // ========================================================================
    // AgentProperties binding
    // ========================================================================

    @Nested
    @DisplayName("AgentProperties binding")
    class AgentPropertiesBindingTests {

        @Test
        @DisplayName("should bind single supported task")
        void shouldBindSingleTask() {
            runner.withPropertyValues(
                    "runtime.mode=DISTRIBUTED",
                    "runtime.role=AGENT",
                    "agent.supported-tasks=mock-server"
            ).run(ctx -> {
                var props = ctx.getBean(AgentProperties.class);
                assertThat(props.supportedTasks()).containsExactly("mock-server");
            });
        }

        @Test
        @DisplayName("should bind multiple supported tasks")
        void shouldBindMultipleTasks() {
            runner.withPropertyValues(
                    "runtime.mode=DISTRIBUTED",
                    "runtime.role=AGENT",
                    "agent.supported-tasks=mock-server,http-client,kafka-producer"
            ).run(ctx -> {
                var props = ctx.getBean(AgentProperties.class);
                assertThat(props.supportedTasks())
                        .containsExactly("mock-server", "http-client", "kafka-producer");
            });
        }

        @Test
        @DisplayName("should default to empty list when not configured")
        void shouldDefaultToEmptyList() {
            runner.withPropertyValues(
                    "runtime.mode=DISTRIBUTED",
                    "runtime.role=AGENT"
            ).run(ctx -> {
                var props = ctx.getBean(AgentProperties.class);
                assertThat(props.supportedTasks()).isEmpty();
            });
        }
    }
}
