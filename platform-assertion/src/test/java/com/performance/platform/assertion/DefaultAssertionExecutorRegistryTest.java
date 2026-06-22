package com.performance.platform.assertion;

import com.performance.platform.domain.assertion.AssertionResult;
import com.performance.platform.domain.execution.ExecutionContext;
import com.performance.platform.domain.scenario.StepDefinition;
import com.performance.platform.plugin.AssertionExecutor;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("DefaultAssertionExecutorRegistry")
class DefaultAssertionExecutorRegistryTest {

    /** Stub AssertionExecutor that returns a fixed assertion name. */
    private static final class StubAssertionExecutor implements AssertionExecutor {
        private final String name;
        private final AssertionResult result;

        StubAssertionExecutor(String name) {
            this(name, null);
        }

        StubAssertionExecutor(String name, AssertionResult result) {
            this.name = name;
            this.result = result;
        }

        @Override public String getSupportedAssertionName() { return name; }

        @Override public AssertionResult evaluate(ExecutionContext ctx, StepDefinition step) {
            return result;
        }
    }

    @Test
    @DisplayName("should register executors from constructor list")
    void shouldRegisterExecutorsFromConstructor() {
        var gatling = new StubAssertionExecutor("gatling-metric");
        var db = new StubAssertionExecutor("database");
        DefaultAssertionExecutorRegistry registry =
                new DefaultAssertionExecutorRegistry(List.of(gatling, db));

        assertThat(registry.getSupportedAssertionNames())
                .containsExactlyInAnyOrder("gatling-metric", "database");
    }

    @Test
    @DisplayName("should resolve executor by assertion name")
    void shouldResolveExecutorByName() {
        var gatling = new StubAssertionExecutor("gatling-metric");
        DefaultAssertionExecutorRegistry registry =
                new DefaultAssertionExecutorRegistry(List.of(gatling));

        AssertionExecutor resolved = registry.getFor("gatling-metric");
        assertThat(resolved).isSameAs(gatling);
    }

    @Test
    @DisplayName("should return empty set when no executors registered")
    void shouldReturnEmptySetWhenNoExecutors() {
        DefaultAssertionExecutorRegistry registry =
                new DefaultAssertionExecutorRegistry(List.of());

        assertThat(registry.getSupportedAssertionNames()).isEmpty();
    }

    @Test
    @DisplayName("should support manual registration after construction")
    void shouldSupportManualRegistration() {
        DefaultAssertionExecutorRegistry registry =
                new DefaultAssertionExecutorRegistry(List.of());
        var kafka = new StubAssertionExecutor("kafka");

        registry.register(kafka);

        assertThat(registry.getSupportedAssertionNames()).contains("kafka");
        assertThat(registry.getFor("kafka")).isSameAs(kafka);
    }

    @Test
    @DisplayName("should replace executor when registering same name")
    void shouldReplaceOnDuplicateName() {
        var v1 = new StubAssertionExecutor("http-mock");
        var v2 = new StubAssertionExecutor("http-mock");
        DefaultAssertionExecutorRegistry registry =
                new DefaultAssertionExecutorRegistry(List.of(v1, v2));

        assertThat(registry.getSupportedAssertionNames()).hasSize(1);
        assertThat(registry.getFor("http-mock")).isSameAs(v2); // dernier gagne
    }

    @Nested
    @DisplayName("Error cases")
    class ErrorCases {

        @Test
        @DisplayName("should throw UnsupportedAssertionNameException for unknown name")
        void shouldThrowForUnknownName() {
            DefaultAssertionExecutorRegistry registry =
                    new DefaultAssertionExecutorRegistry(List.of(
                            new StubAssertionExecutor("gatling-metric")));

            assertThatThrownBy(() -> registry.getFor("unknown"))
                    .isInstanceOf(UnsupportedAssertionNameException.class)
                    .hasMessageContaining("unknown");
        }

        @Test
        @DisplayName("should throw NPE when registering null executor")
        void shouldThrowNpeOnNullRegister() {
            DefaultAssertionExecutorRegistry registry =
                    new DefaultAssertionExecutorRegistry(List.of());

            assertThatThrownBy(() -> registry.register(null))
                    .isInstanceOf(NullPointerException.class);
        }

        @Test
        @DisplayName("should handle empty constructor list gracefully")
        void shouldHandleEmptyConstructorList() {
            DefaultAssertionExecutorRegistry registry =
                    new DefaultAssertionExecutorRegistry(List.of());

            assertThat(registry.getSupportedAssertionNames()).isEmpty();
        }
    }

    @Nested
    @DisplayName("Multiple executors")
    class MultipleExecutors {

        @Test
        @DisplayName("should register and resolve many executors")
        void shouldRegisterManyExecutors() {
            List<AssertionExecutor> executors = List.of(
                    new StubAssertionExecutor("gatling-metric"),
                    new StubAssertionExecutor("database"),
                    new StubAssertionExecutor("kafka"),
                    new StubAssertionExecutor("http-mock"),
                    new StubAssertionExecutor("file")
            );
            DefaultAssertionExecutorRegistry registry =
                    new DefaultAssertionExecutorRegistry(executors);

            assertThat(registry.getSupportedAssertionNames()).hasSize(5);
            assertThat(registry.getFor("kafka")).isNotNull();
            assertThat(registry.getFor("file")).isNotNull();
            assertThat(registry.getFor("database")).isNotNull();
        }
    }

    @Test
    @DisplayName("should return immutable copy of supported names")
    void shouldReturnImmutableCopyOfSupportedNames() {
        DefaultAssertionExecutorRegistry registry =
                new DefaultAssertionExecutorRegistry(List.of(
                        new StubAssertionExecutor("gatling-metric")));

        Set<String> names = registry.getSupportedAssertionNames();
        assertThatThrownBy(() -> names.add("new"))
                .isInstanceOf(UnsupportedOperationException.class);
    }
}
