package com.performance.platform.app.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.BindResult;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests de binding pour {@link AgentProperties}.
 * <p>
 * Utilise {@link Binder} directement pour tester le mapping des proprietes
 * sans charger le contexte Spring complet.
 * <p>
 * Le binding de variable d'environnement est teste separement via
 * un {@link MapConfigurationPropertySource} simulant les valeurs aplaties
 * que Spring Boot recoit des variables d'environnement.
 */
@DisplayName("AgentProperties")
class AgentPropertiesTest {

    @Nested
    @DisplayName("Default values")
    class DefaultValues {

        @Test
        @DisplayName("should return empty list when property is not set")
        void shouldReturnEmptyListWhenNotSet() {
            // When no agent.* properties exist at all, Spring won't bind the
            // record automatically. But the compact constructor handles null
            // defensively — verifying the default behavior directly.
            var props = new AgentProperties(null);
            assertThat(props.supportedTasks()).isEmpty();

            props = new AgentProperties(List.of());
            assertThat(props.supportedTasks()).isEmpty();
        }
    }

    @Nested
    @DisplayName("YAML list binding")
    class YamlListBinding {

        @Test
        @DisplayName("should bind list of two task names")
        void shouldBindListOfTwoTaskNames() {
            var source = Map.of(
                    "agent.supported-tasks[0]", "mock-server",
                    "agent.supported-tasks[1]", "http-client"
            );
            var binder = new Binder(new MapConfigurationPropertySource(source));
            var result = binder.bind("agent", AgentProperties.class);

            assertThat(result.isBound()).isTrue();
            assertThat(result.get().supportedTasks())
                    .containsExactly("mock-server", "http-client");
        }

        @Test
        @DisplayName("should bind single task name")
        void shouldBindSingleTaskName() {
            var source = Map.of("agent.supported-tasks[0]", "mock-server");
            var binder = new Binder(new MapConfigurationPropertySource(source));
            var result = binder.bind("agent", AgentProperties.class);

            assertThat(result.isBound()).isTrue();
            assertThat(result.get().supportedTasks())
                    .containsExactly("mock-server");
        }

        @Test
        @DisplayName("should preserve duplicate task names")
        void shouldPreserveDuplicateTaskNames() {
            var source = Map.of(
                    "agent.supported-tasks[0]", "a",
                    "agent.supported-tasks[1]", "b",
                    "agent.supported-tasks[2]", "a"
            );
            var binder = new Binder(new MapConfigurationPropertySource(source));
            var result = binder.bind("agent", AgentProperties.class);

            assertThat(result.isBound()).isTrue();
            // Duplicates are preserved — deduplication is the consumer's responsibility
            assertThat(result.get().supportedTasks())
                    .containsExactly("a", "b", "a");
        }
    }

    @Nested
    @DisplayName("Environment variable simulation")
    class EnvVarSimulation {

        @Test
        @DisplayName("should bind comma-separated env var via relaxed binding")
        void shouldBindCommaSeparatedEnvVar() {
            // Spring Boot relaxed binding: AGENT_SUPPORTED_TASKS env var
            // is mapped as a single string value which Spring splits by comma
            // into a List<String> automatically
            var source = Map.of("agent.supported-tasks", "mock-server,http-client,gatling");
            var binder = new Binder(new MapConfigurationPropertySource(source));
            var result = binder.bind("agent", AgentProperties.class);

            assertThat(result.isBound()).isTrue();
            assertThat(result.get().supportedTasks())
                    .containsExactly("mock-server", "http-client", "gatling");
        }

        @Test
        @DisplayName("should bind single value env var")
        void shouldBindSingleValueEnvVar() {
            var source = Map.of("agent.supported-tasks", "mock-server");
            var binder = new Binder(new MapConfigurationPropertySource(source));
            var result = binder.bind("agent", AgentProperties.class);

            assertThat(result.isBound()).isTrue();
            assertThat(result.get().supportedTasks())
                    .containsExactly("mock-server");
        }

        @Test
        @DisplayName("should return empty list for empty env var value")
        void shouldReturnEmptyListForEmptyEnvVar() {
            // When AGENT_SUPPORTED_TASKS is set to empty string, Spring's
            // comma-split produces [""]. The compact constructor filters
            // blank entries, resulting in an empty list.
            var source = Map.of("agent.supported-tasks", "");
            var binder = new Binder(new MapConfigurationPropertySource(source));
            var result = binder.bind("agent", AgentProperties.class);

            assertThat(result.isBound()).isTrue();
            assertThat(result.get().supportedTasks()).isEmpty();
        }
    }

    @Nested
    @DisplayName("Immutability")
    class Immutability {

        @Test
        @DisplayName("should throw on mutation attempt")
        void shouldThrowOnMutationAttempt() {
            var source = Map.of(
                    "agent.supported-tasks[0]", "mock-server",
                    "agent.supported-tasks[1]", "http-client"
            );
            var binder = new Binder(new MapConfigurationPropertySource(source));
            var result = binder.bind("agent", AgentProperties.class);

            var tasks = result.get().supportedTasks();

            assertThatThrownBy(() -> tasks.add("extra"))
                    .isInstanceOf(UnsupportedOperationException.class);
            assertThatThrownBy(() -> tasks.remove(0))
                    .isInstanceOf(UnsupportedOperationException.class);
        }
    }
}
