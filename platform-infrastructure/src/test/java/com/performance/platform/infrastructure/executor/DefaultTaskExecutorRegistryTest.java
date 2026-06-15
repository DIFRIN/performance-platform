package com.performance.platform.infrastructure.executor;

import com.performance.platform.domain.execution.ExecutionContext;
import com.performance.platform.domain.id.TaskId;
import com.performance.platform.domain.scenario.StepDefinition;
import com.performance.platform.domain.task.TaskResult;
import com.performance.platform.plugin.TaskExecutor;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("DefaultTaskExecutorRegistry")
class DefaultTaskExecutorRegistryTest {

    /**
     * Cree un stub d'executor retournant un taskName fixe.
     * N'utilise pas Mockito pour eviter les incompatibilites ByteBuddy/Java 25.
     */
    private static TaskExecutor stubExecutor(String taskName) {
        return new TaskExecutor() {
            @Override
            public TaskResult execute(ExecutionContext context, StepDefinition step) {
                return TaskResult.success(new TaskId("stub"), "stub", Duration.ZERO, Map.of());
            }

            @Override
            public String getSupportedTaskName() {
                return taskName;
            }
        };
    }

    /**
     * Cree un stub d'executor dont getSupportedTaskName() retourne null.
     */
    private static TaskExecutor nullTaskNameExecutor() {
        return new TaskExecutor() {
            @Override
            public TaskResult execute(ExecutionContext context, StepDefinition step) {
                return TaskResult.success(new TaskId("stub"), "stub", Duration.ZERO, Map.of());
            }

            @Override
            public String getSupportedTaskName() {
                return null;
            }
        };
    }

    @Nested
    @DisplayName("Constructor injection")
    class ConstructorInjection {

        @Test
        @DisplayName("should register all executors from list")
        void shouldRegisterAllExecutorsFromList() {
            TaskExecutor e1 = stubExecutor("database");
            TaskExecutor e2 = stubExecutor("kafka-producer");
            TaskExecutor e3 = stubExecutor("http-request");

            DefaultTaskExecutorRegistry registry = new DefaultTaskExecutorRegistry(List.of(e1, e2, e3));

            assertThat(registry.getSupportedTaskNames())
                    .containsExactlyInAnyOrder("database", "kafka-producer", "http-request");
        }

        @Test
        @DisplayName("should handle empty list gracefully")
        void shouldHandleEmptyListGracefully() {
            DefaultTaskExecutorRegistry registry = new DefaultTaskExecutorRegistry(Collections.emptyList());

            assertThat(registry.getSupportedTaskNames()).isEmpty();
        }

        @Test
        @DisplayName("should handle list with one element")
        void shouldHandleSingleElement() {
            TaskExecutor executor = stubExecutor("database");
            DefaultTaskExecutorRegistry registry = new DefaultTaskExecutorRegistry(List.of(executor));

            assertThat(registry.getSupportedTaskNames()).containsExactly("database");
        }
    }

    @Nested
    @DisplayName("register()")
    class Register {

        @Test
        @DisplayName("should add new executor to registry")
        void shouldAddNewExecutorToRegistry() {
            DefaultTaskExecutorRegistry registry = new DefaultTaskExecutorRegistry(Collections.emptyList());
            TaskExecutor executor = stubExecutor("database");

            registry.register(executor);

            assertThat(registry.getSupportedTaskNames()).containsExactly("database");
        }

        @Test
        @DisplayName("should replace existing executor with same taskName")
        void shouldReplaceExistingExecutorWithSameTaskName() {
            DefaultTaskExecutorRegistry registry = new DefaultTaskExecutorRegistry(Collections.emptyList());
            TaskExecutor first = stubExecutor("database");
            TaskExecutor second = stubExecutor("database");

            registry.register(first);
            registry.register(second);

            assertThat(registry.getSupportedTaskNames()).hasSize(1).contains("database");
            assertThat(registry.getFor("database")).isSameAs(second);
        }

        @Test
        @DisplayName("should throw NullPointerException when executor is null")
        void shouldThrowWhenExecutorIsNull() {
            DefaultTaskExecutorRegistry registry = new DefaultTaskExecutorRegistry(Collections.emptyList());

            assertThatThrownBy(() -> registry.register(null))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("executor must not be null");
        }

        @Test
        @DisplayName("should throw when executor taskName is null")
        void shouldThrowWhenExecutorTaskNameIsNull() {
            DefaultTaskExecutorRegistry registry = new DefaultTaskExecutorRegistry(Collections.emptyList());

            assertThatThrownBy(() -> registry.register(nullTaskNameExecutor()))
                    .isInstanceOf(NullPointerException.class);
        }
    }

    @Nested
    @DisplayName("getFor()")
    class GetFor {

        @Test
        @DisplayName("should return the correct executor for a registered taskName")
        void shouldReturnCorrectExecutorForRegisteredTaskName() {
            TaskExecutor dbExecutor = stubExecutor("database");
            DefaultTaskExecutorRegistry registry = new DefaultTaskExecutorRegistry(List.of(dbExecutor));

            TaskExecutor resolved = registry.getFor("database");

            assertThat(resolved).isSameAs(dbExecutor);
        }

        @Test
        @DisplayName("should throw UnsupportedTaskNameException for unknown taskName")
        void shouldThrowForUnknownTaskName() {
            DefaultTaskExecutorRegistry registry = new DefaultTaskExecutorRegistry(Collections.emptyList());

            assertThatThrownBy(() -> registry.getFor("unknown"))
                    .isInstanceOf(UnsupportedTaskNameException.class)
                    .hasMessageContaining("unknown");
        }

        @Test
        @DisplayName("should return correct executor when multiple are registered")
        void shouldReturnCorrectExecutorWhenMultipleRegistered() {
            TaskExecutor dbExecutor = stubExecutor("database");
            TaskExecutor kafkaExecutor = stubExecutor("kafka-producer");
            TaskExecutor httpExecutor = stubExecutor("http-request");
            DefaultTaskExecutorRegistry registry = new DefaultTaskExecutorRegistry(
                    List.of(dbExecutor, kafkaExecutor, httpExecutor));

            assertThat(registry.getFor("database")).isSameAs(dbExecutor);
            assertThat(registry.getFor("kafka-producer")).isSameAs(kafkaExecutor);
            assertThat(registry.getFor("http-request")).isSameAs(httpExecutor);
        }
    }

    @Nested
    @DisplayName("getSupportedTaskNames()")
    class GetSupportedTaskNames {

        @Test
        @DisplayName("should return empty set for empty registry")
        void shouldReturnEmptySetForEmptyRegistry() {
            DefaultTaskExecutorRegistry registry = new DefaultTaskExecutorRegistry(Collections.emptyList());

            Set<String> names = registry.getSupportedTaskNames();

            assertThat(names).isEmpty();
        }

        @Test
        @DisplayName("should return all registered task names")
        void shouldReturnAllRegisteredTaskNames() {
            DefaultTaskExecutorRegistry registry = new DefaultTaskExecutorRegistry(
                    List.of(stubExecutor("database"), stubExecutor("http-request")));

            Set<String> names = registry.getSupportedTaskNames();

            assertThat(names).containsExactlyInAnyOrder("database", "http-request");
        }

        @Test
        @DisplayName("should return unmodifiable set")
        void shouldReturnUnmodifiableSet() {
            DefaultTaskExecutorRegistry registry = new DefaultTaskExecutorRegistry(
                    List.of(stubExecutor("database")));

            Set<String> names = registry.getSupportedTaskNames();

            assertThatThrownBy(() -> names.add("new-task"))
                    .isInstanceOf(UnsupportedOperationException.class);
        }
    }

    @Nested
    @DisplayName("UnsupportedTaskNameException")
    class UnsupportedTaskNameExceptionTests {

        @Test
        @DisplayName("should store the taskName that caused the exception")
        void shouldStoreTaskName() {
            UnsupportedTaskNameException exception = new UnsupportedTaskNameException("missing-executor");

            assertThat(exception.getTaskName()).isEqualTo("missing-executor");
            assertThat(exception.getMessage()).contains("missing-executor");
        }

        @Test
        @DisplayName("should be a RuntimeException")
        void shouldBeRuntimeException() {
            UnsupportedTaskNameException exception = new UnsupportedTaskNameException("test");

            assertThat(exception).isInstanceOf(RuntimeException.class);
        }
    }
}
