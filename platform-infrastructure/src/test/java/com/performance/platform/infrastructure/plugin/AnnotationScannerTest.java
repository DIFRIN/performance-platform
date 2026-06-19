package com.performance.platform.infrastructure.plugin;

import com.performance.platform.domain.execution.ExecutionContext;
import com.performance.platform.domain.scenario.Phase;
import com.performance.platform.domain.scenario.StepDefinition;
import com.performance.platform.domain.task.TaskResult;
import com.performance.platform.plugin.Assertion;
import com.performance.platform.plugin.Injection;
import com.performance.platform.plugin.Preparation;
import com.performance.platform.plugin.TaskExecutor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

/**
 * Tests for {@link DefaultAnnotationScanner}.
 *
 * <p>Verifies annotation detection, metadata extraction, phase mapping,
 * and edge cases (no annotation, multiple annotations, null candidate).</p>
 */
@DisplayName("DefaultAnnotationScanner")
class AnnotationScannerTest {

    private AnnotationScanner scanner;

    // --- Test fixtures: annotated mock executors ---

    @Preparation(name = "db-prep", version = "2.1.0", description = "Database preparation task")
    public static class PreparationPlugin implements TaskExecutor {
        @Override
        public TaskResult execute(ExecutionContext context, StepDefinition step) {
            return TaskResult.success(step.id(), "db-prep", Duration.ofMillis(1), Map.of());
        }

        @Override
        public String getSupportedTaskName() {
            return "db-prep";
        }
    }

    @Injection(name = "load-inject", version = "1.5.0", description = "Load injection task")
    public static class InjectionPlugin implements TaskExecutor {
        @Override
        public TaskResult execute(ExecutionContext context, StepDefinition step) {
            return TaskResult.success(step.id(), "load-inject", Duration.ofMillis(1), Map.of());
        }

        @Override
        public String getSupportedTaskName() {
            return "load-inject";
        }
    }

    @Assertion(name = "metric-check", version = "3.0.0", description = "Metric assertion task")
    public static class AssertionPlugin implements TaskExecutor {
        @Override
        public TaskResult execute(ExecutionContext context, StepDefinition step) {
            return TaskResult.success(step.id(), "metric-check", Duration.ofMillis(1), Map.of());
        }

        @Override
        public String getSupportedTaskName() {
            return "metric-check";
        }
    }

    /** Class with no plugin annotation at all. */
    public static class NoAnnotationClass implements TaskExecutor {
        @Override
        public TaskResult execute(ExecutionContext context, StepDefinition step) {
            return TaskResult.success(step.id(), "no-annotation", Duration.ofMillis(1), Map.of());
        }

        @Override
        public String getSupportedTaskName() {
            return "no-annotation";
        }
    }

    /** Class annotated with multiple plugin annotations — ambiguous. */
    @Preparation(name = "ambiguous-prep", version = "1.0.0")
    @Injection(name = "ambiguous-inject", version = "1.0.0")
    public static class MultiAnnotationPlugin implements TaskExecutor {
        @Override
        public TaskResult execute(ExecutionContext context, StepDefinition step) {
            return TaskResult.success(step.id(), "ambiguous", Duration.ofMillis(1), Map.of());
        }

        @Override
        public String getSupportedTaskName() {
            return "ambiguous-prep";
        }
    }

    @BeforeEach
    void setUp() {
        scanner = new DefaultAnnotationScanner();
    }

    // --- Nominal cases: exactly one annotation ---

    @Nested
    @DisplayName("Exactly one annotation")
    class SingleAnnotation {

        @Test
        @DisplayName("@Preparation returns PREPARATION descriptor")
        void preparationAnnotation() {
            Optional<PluginDescriptor> result = scanner.scan(PreparationPlugin.class);

            assertThat(result).isPresent();
            PluginDescriptor desc = result.get();
            assertThat(desc.name()).isEqualTo("db-prep");
            assertThat(desc.version()).isEqualTo("2.1.0");
            assertThat(desc.description()).isEqualTo("Database preparation task");
            assertThat(desc.phase()).isEqualTo(Phase.PREPARATION);
            assertThat(desc.executorClass()).isEqualTo(PreparationPlugin.class);
        }

        @Test
        @DisplayName("@Injection returns INJECTION descriptor")
        void injectionAnnotation() {
            Optional<PluginDescriptor> result = scanner.scan(InjectionPlugin.class);

            assertThat(result).isPresent();
            PluginDescriptor desc = result.get();
            assertThat(desc.name()).isEqualTo("load-inject");
            assertThat(desc.version()).isEqualTo("1.5.0");
            assertThat(desc.description()).isEqualTo("Load injection task");
            assertThat(desc.phase()).isEqualTo(Phase.INJECTION);
            assertThat(desc.executorClass()).isEqualTo(InjectionPlugin.class);
        }

        @Test
        @DisplayName("@Assertion returns ASSERTION descriptor")
        void assertionAnnotation() {
            Optional<PluginDescriptor> result = scanner.scan(AssertionPlugin.class);

            assertThat(result).isPresent();
            PluginDescriptor desc = result.get();
            assertThat(desc.name()).isEqualTo("metric-check");
            assertThat(desc.version()).isEqualTo("3.0.0");
            assertThat(desc.description()).isEqualTo("Metric assertion task");
            assertThat(desc.phase()).isEqualTo(Phase.ASSERTION);
            assertThat(desc.executorClass()).isEqualTo(AssertionPlugin.class);
        }

        @Test
        @DisplayName("descriptor preserves default annotation values")
        void defaultAnnotationValues() {
            // Uses annotation with minimal config (only name, version/description defaulted)
            Optional<PluginDescriptor> result = scanner.scan(MinimalAnnotationPlugin.class);

            assertThat(result).isPresent();
            PluginDescriptor desc = result.get();
            assertThat(desc.name()).isEqualTo("minimal");
            assertThat(desc.version()).isEqualTo("1.0.0"); // default
            assertThat(desc.description()).isEmpty(); // default
            assertThat(desc.phase()).isEqualTo(Phase.PREPARATION);
        }
    }

    // --- Edge cases: zero or multiple annotations ---

    @Nested
    @DisplayName("Zero or multiple annotations")
    class EdgeCases {

        @Test
        @DisplayName("no annotation returns empty")
        void noAnnotation() {
            Optional<PluginDescriptor> result = scanner.scan(NoAnnotationClass.class);

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("multiple annotations returns empty")
        void multipleAnnotations() {
            Optional<PluginDescriptor> result = scanner.scan(MultiAnnotationPlugin.class);

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("null candidate throws NullPointerException")
        void nullCandidateThrows() {
            assertThatThrownBy(() -> scanner.scan(null))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("candidate must not be null");
        }
    }

    // --- PluginDescriptor validation ---

    @Nested
    @DisplayName("PluginDescriptor contract validation")
    class PluginDescriptorValidation {

        @Test
        @DisplayName("valid descriptor constructed without exception")
        void validDescriptor() {
            assertDoesNotThrow(() -> new PluginDescriptor(
                    "test", "1.0.0", "desc", Phase.PREPARATION, PreparationPlugin.class));
        }

        @Test
        @DisplayName("null name throws NullPointerException")
        void nullNameThrows() {
            assertThatThrownBy(() -> new PluginDescriptor(
                    null, "1.0.0", "desc", Phase.PREPARATION, PreparationPlugin.class))
                    .isInstanceOf(NullPointerException.class);
        }

        @Test
        @DisplayName("blank name throws IllegalArgumentException")
        void blankNameThrows() {
            assertThatThrownBy(() -> new PluginDescriptor(
                    "  ", "1.0.0", "desc", Phase.PREPARATION, PreparationPlugin.class))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("name must not be blank");
        }

        @Test
        @DisplayName("null version throws NullPointerException")
        void nullVersionThrows() {
            assertThatThrownBy(() -> new PluginDescriptor(
                    "test", null, "desc", Phase.PREPARATION, PreparationPlugin.class))
                    .isInstanceOf(NullPointerException.class);
        }

        @Test
        @DisplayName("blank version throws IllegalArgumentException")
        void blankVersionThrows() {
            assertThatThrownBy(() -> new PluginDescriptor(
                    "test", "  ", "desc", Phase.PREPARATION, PreparationPlugin.class))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("version must not be blank");
        }

        @Test
        @DisplayName("null description throws NullPointerException")
        void nullDescriptionThrows() {
            assertThatThrownBy(() -> new PluginDescriptor(
                    "test", "1.0.0", null, Phase.PREPARATION, PreparationPlugin.class))
                    .isInstanceOf(NullPointerException.class);
        }

        @Test
        @DisplayName("null phase throws NullPointerException")
        void nullPhaseThrows() {
            assertThatThrownBy(() -> new PluginDescriptor(
                    "test", "1.0.0", "desc", null, PreparationPlugin.class))
                    .isInstanceOf(NullPointerException.class);
        }

        @Test
        @DisplayName("null executorClass throws NullPointerException")
        void nullExecutorClassThrows() {
            assertThatThrownBy(() -> new PluginDescriptor(
                    "test", "1.0.0", "desc", Phase.PREPARATION, null))
                    .isInstanceOf(NullPointerException.class);
        }
    }

    // --- Minimal annotation fixture (only name, defaults for version/description) ---

    @Preparation(name = "minimal")
    public static class MinimalAnnotationPlugin implements TaskExecutor {
        @Override
        public TaskResult execute(ExecutionContext context, StepDefinition step) {
            return TaskResult.success(step.id(), "minimal", Duration.ofMillis(1), Map.of());
        }

        @Override
        public String getSupportedTaskName() {
            return "minimal";
        }
    }
}
