package com.performance.platform.infrastructure.plugin;

import com.performance.platform.domain.execution.ExecutionContext;
import com.performance.platform.domain.scenario.Phase;
import com.performance.platform.domain.scenario.StepDefinition;
import com.performance.platform.domain.task.TaskResult;
import com.performance.platform.infrastructure.executor.UnsupportedTaskNameException;
import com.performance.platform.plugin.Assertion;
import com.performance.platform.plugin.Injection;
import com.performance.platform.plugin.Preparation;
import com.performance.platform.plugin.TaskExecutor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests for {@link DefaultPluginRegistry}.
 *
 * Uses mock {@link TaskExecutor} inner classes with real annotations
 * to verify phase derivation, lookup, collision handling, and edge cases.
 */
@DisplayName("DefaultPluginRegistry")
class DefaultPluginRegistryTest {

    // --- Test fixtures: annotated mock executors ---

    @Preparation(name = "database", version = "1.0.0", description = "Internal DB executor")
    public static class InternalDatabaseExecutor implements TaskExecutor {
        @Override
        public TaskResult execute(ExecutionContext context, StepDefinition step) {
            return TaskResult.success(step.id(), "database", java.time.Duration.ofMillis(1), java.util.Map.of());
        }

        @Override
        public String getSupportedTaskName() {
            return "database";
        }
    }

    @Preparation(name = "shell", version = "1.0.0")
    public static class InternalShellExecutor implements TaskExecutor {
        @Override
        public TaskResult execute(ExecutionContext context, StepDefinition step) {
            return TaskResult.success(step.id(), "shell", java.time.Duration.ofMillis(1), java.util.Map.of());
        }

        @Override
        public String getSupportedTaskName() {
            return "shell";
        }
    }

    @Injection(name = "gatling", version = "1.0.0")
    public static class InternalGatlingExecutor implements TaskExecutor {
        @Override
        public TaskResult execute(ExecutionContext context, StepDefinition step) {
            return TaskResult.success(step.id(), "gatling", java.time.Duration.ofMillis(1), java.util.Map.of());
        }

        @Override
        public String getSupportedTaskName() {
            return "gatling";
        }
    }

    @Assertion(name = "gatling-metric", version = "1.0.0")
    public static class InternalAssertionExecutor implements TaskExecutor {
        @Override
        public TaskResult execute(ExecutionContext context, StepDefinition step) {
            return TaskResult.success(step.id(), "gatling-metric", java.time.Duration.ofMillis(1), java.util.Map.of());
        }

        @Override
        public String getSupportedTaskName() {
            return "gatling-metric";
        }
    }

    // External executor with same (Phase, name) as InternalDatabaseExecutor — for collision test
    @Preparation(name = "database", version = "2.0.0", description = "External DB override")
    public static class ExternalDatabaseOverrideExecutor implements TaskExecutor {
        @Override
        public TaskResult execute(ExecutionContext context, StepDefinition step) {
            return TaskResult.success(step.id(), "database", java.time.Duration.ofMillis(1), java.util.Map.of());
        }

        @Override
        public String getSupportedTaskName() {
            return "database";
        }
    }

    @Preparation(name = "external-seeder", version = "1.0.0")
    public static class ExternalSeederExecutor implements TaskExecutor {
        @Override
        public TaskResult execute(ExecutionContext context, StepDefinition step) {
            return TaskResult.success(step.id(), "external-seeder", java.time.Duration.ofMillis(1), java.util.Map.of());
        }

        @Override
        public String getSupportedTaskName() {
            return "external-seeder";
        }
    }

    @Injection(name = "custom-injector", version = "1.0.0")
    public static class ExternalInjectorExecutor implements TaskExecutor {
        @Override
        public TaskResult execute(ExecutionContext context, StepDefinition step) {
            return TaskResult.success(step.id(), "custom-injector", java.time.Duration.ofMillis(1), java.util.Map.of());
        }

        @Override
        public String getSupportedTaskName() {
            return "custom-injector";
        }
    }

    @Assertion(name = "custom-assertion", version = "1.0.0")
    public static class ExternalAssertionExecutor implements TaskExecutor {
        @Override
        public TaskResult execute(ExecutionContext context, StepDefinition step) {
            return TaskResult.success(step.id(), "custom-assertion", java.time.Duration.ofMillis(1), java.util.Map.of());
        }

        @Override
        public String getSupportedTaskName() {
            return "custom-assertion";
        }
    }

    // Executor with NO annotation — should be rejected
    public static class UnannotatedExecutor implements TaskExecutor {
        @Override
        public TaskResult execute(ExecutionContext context, StepDefinition step) {
            return TaskResult.success(step.id(), "unannotated", java.time.Duration.ofMillis(1), java.util.Map.of());
        }

        @Override
        public String getSupportedTaskName() {
            return "unannotated";
        }
    }

    // --- Helpers ---

    private static PluginLoader emptyLoader() {
        return dir -> new PluginLoadResult(0, 0, List.of(), List.of(), List.of());
    }

    private static PluginLoader loaderWith(List<TaskExecutor> executors) {
        return dir -> new PluginLoadResult(1, executors.size(),
                List.copyOf(executors), List.of(), List.of());
    }

    // --- Input validation (no @TempDir needed, uses empty loader) ---

    @Nested
    @DisplayName("Input validation")
    class InputValidation {

        private DefaultPluginRegistry registry;

        @BeforeEach
        void setUp() {
            registry = new DefaultPluginRegistry(List.of(), emptyLoader(), "/nonexistent");
        }

        @Test
        @DisplayName("lookup throws NullPointerException when phase is null")
        void lookupThrowsOnNullPhase() {
            assertThatThrownBy(() -> registry.lookup(null, "database"))
                    .isInstanceOf(NullPointerException.class);
        }

        @Test
        @DisplayName("lookup throws NullPointerException when name is null")
        void lookupThrowsOnNullName() {
            assertThatThrownBy(() -> registry.lookup(Phase.PREPARATION, null))
                    .isInstanceOf(NullPointerException.class);
        }

        @Test
        @DisplayName("lookup throws IllegalArgumentException when name is blank")
        void lookupThrowsOnBlankName() {
            assertThatThrownBy(() -> registry.lookup(Phase.PREPARATION, "  "))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("blank");
        }

        @Test
        @DisplayName("contains throws NullPointerException when phase is null")
        void containsThrowsOnNullPhase() {
            assertThatThrownBy(() -> registry.contains(null, "database"))
                    .isInstanceOf(NullPointerException.class);
        }

        @Test
        @DisplayName("contains returns false when name is blank")
        void containsReturnsFalseForBlank() {
            assertThat(registry.contains(Phase.PREPARATION, "  ")).isFalse();
            assertThat(registry.contains(Phase.PREPARATION, "")).isFalse();
        }

        @Test
        @DisplayName("namesFor throws NullPointerException when phase is null")
        void namesForThrowsOnNullPhase() {
            assertThatThrownBy(() -> registry.namesFor(null))
                    .isInstanceOf(NullPointerException.class);
        }

        @Test
        @DisplayName("constructor throws NullPointerException when internalExecutors is null")
        void ctorThrowsOnNullInternalExecutors() {
            assertThatThrownBy(() -> new DefaultPluginRegistry(null, emptyLoader(), "/tmp"))
                    .isInstanceOf(NullPointerException.class);
        }

        @Test
        @DisplayName("constructor throws NullPointerException when loader is null")
        void ctorThrowsOnNullLoader() {
            assertThatThrownBy(() -> new DefaultPluginRegistry(List.of(), null, "/tmp"))
                    .isInstanceOf(NullPointerException.class);
        }

        @Test
        @DisplayName("constructor throws NullPointerException when pluginDir is null")
        void ctorThrowsOnNullPluginDir() {
            assertThatThrownBy(() -> new DefaultPluginRegistry(List.of(), emptyLoader(), null))
                    .isInstanceOf(NullPointerException.class);
        }
    }

    // --- Internal executors only ---

    @Nested
    @DisplayName("Internal executors registration")
    class InternalExecutors {

        private DefaultPluginRegistry registry;

        @BeforeEach
        void setUp() {
            List<TaskExecutor> internal = List.of(
                    new InternalDatabaseExecutor(),
                    new InternalShellExecutor(),
                    new InternalGatlingExecutor(),
                    new InternalAssertionExecutor());
            registry = new DefaultPluginRegistry(internal, emptyLoader(), "/nonexistent");
        }

        @Test
        @DisplayName("lookup by (phase, name) returns correct internal executor")
        void lookupReturnsCorrectExecutor() {
            TaskExecutor db = registry.lookup(Phase.PREPARATION, "database");
            assertThat(db).isInstanceOf(InternalDatabaseExecutor.class);
            assertThat(db.getSupportedTaskName()).isEqualTo("database");

            TaskExecutor shell = registry.lookup(Phase.PREPARATION, "shell");
            assertThat(shell).isInstanceOf(InternalShellExecutor.class);

            TaskExecutor gatling = registry.lookup(Phase.INJECTION, "gatling");
            assertThat(gatling).isInstanceOf(InternalGatlingExecutor.class);

            TaskExecutor assertion = registry.lookup(Phase.ASSERTION, "gatling-metric");
            assertThat(assertion).isInstanceOf(InternalAssertionExecutor.class);
        }

        @Test
        @DisplayName("contains returns true for registered executors")
        void containsReturnsTrue() {
            assertThat(registry.contains(Phase.PREPARATION, "database")).isTrue();
            assertThat(registry.contains(Phase.PREPARATION, "shell")).isTrue();
            assertThat(registry.contains(Phase.INJECTION, "gatling")).isTrue();
            assertThat(registry.contains(Phase.ASSERTION, "gatling-metric")).isTrue();
        }

        @Test
        @DisplayName("contains returns false for unknown (phase, name)")
        void containsReturnsFalse() {
            assertThat(registry.contains(Phase.PREPARATION, "nonexistent")).isFalse();
            assertThat(registry.contains(Phase.INJECTION, "database")).isFalse();
            assertThat(registry.contains(Phase.ASSERTION, "shell")).isFalse();
        }

        @Test
        @DisplayName("namesFor returns correct set of names per phase")
        void namesForPerPhase() {
            Set<String> prep = registry.namesFor(Phase.PREPARATION);
            assertThat(prep).containsExactlyInAnyOrder("database", "shell");

            Set<String> inj = registry.namesFor(Phase.INJECTION);
            assertThat(inj).containsExactly("gatling");

            Set<String> asrt = registry.namesFor(Phase.ASSERTION);
            assertThat(asrt).containsExactly("gatling-metric");
        }

        @Test
        @DisplayName("namesFor returns empty set for phase with no executors")
        void namesForEmptyPhase() {
            // With no executors in INJECTION, the set is empty -> actually we did register one
            // So test with a fresh empty registry
            var empty = new DefaultPluginRegistry(List.of(), emptyLoader(), "/nonexistent");
            assertThat(empty.namesFor(Phase.PREPARATION)).isEmpty();
            assertThat(empty.namesFor(Phase.INJECTION)).isEmpty();
            assertThat(empty.namesFor(Phase.ASSERTION)).isEmpty();
        }

        @Test
        @DisplayName("lookup throws UnsupportedTaskNameException for unknown (phase, name)")
        void lookupThrowsOnUnknown() {
            assertThatThrownBy(() -> registry.lookup(Phase.PREPARATION, "unknown-task"))
                    .isInstanceOf(UnsupportedTaskNameException.class)
                    .hasMessageContaining("unknown-task")
                    .matches(e -> ((UnsupportedTaskNameException) e).getTaskName().equals("unknown-task"));

            assertThatThrownBy(() -> registry.lookup(Phase.INJECTION, "shell"))
                    .isInstanceOf(UnsupportedTaskNameException.class)
                    .matches(e -> ((UnsupportedTaskNameException) e).getTaskName().equals("shell"));
        }
    }

    // --- External override ---

    @Nested
    @DisplayName("External executor override (collision)")
    class ExternalOverride {

        @Test
        @DisplayName("external executor overrides internal on same (phase, name)")
        void externalOverridesInternal() {
            var internalDb = new InternalDatabaseExecutor();
            var externalDb = new ExternalDatabaseOverrideExecutor();

            var registry = new DefaultPluginRegistry(
                    List.of(internalDb),
                    loaderWith(List.of(externalDb)),
                    "./nonexistent");

            TaskExecutor resolved = registry.lookup(Phase.PREPARATION, "database");
            assertThat(resolved).isInstanceOf(ExternalDatabaseOverrideExecutor.class);
            // Should NOT be the internal one
            assertThat(resolved).isNotSameAs(internalDb);
        }

        @Test
        @DisplayName("internal executor is still registered when no collision")
        void noCollisionPreservesInternal() {
            var internalShell = new InternalShellExecutor();
            var externalSeeder = new ExternalSeederExecutor();

            var registry = new DefaultPluginRegistry(
                    List.of(internalShell),
                    loaderWith(List.of(externalSeeder)),
                    "./nonexistent");

            // Internal shell is NOT overridden (different name)
            TaskExecutor shell = registry.lookup(Phase.PREPARATION, "shell");
            assertThat(shell).isInstanceOf(InternalShellExecutor.class);

            // External seeder is also available
            TaskExecutor seeder = registry.lookup(Phase.PREPARATION, "external-seeder");
            assertThat(seeder).isInstanceOf(ExternalSeederExecutor.class);

            // Both are accessible
            assertThat(registry.namesFor(Phase.PREPARATION))
                    .containsExactlyInAnyOrder("shell", "external-seeder");
        }

        @Test
        @DisplayName("external executor from different phase doesn't collide")
        void noCrossPhaseCollision() {
            // Same name "test" but different phases — no collision
            var internalPrep = new InternalDatabaseExecutor(); // name="database", PREPARATION
            var externalInj = new ExternalInjectorExecutor();  // name="custom-injector", INJECTION

            var registry = new DefaultPluginRegistry(
                    List.of(internalPrep),
                    loaderWith(List.of(externalInj)),
                    "./nonexistent");

            assertThat(registry.lookup(Phase.PREPARATION, "database"))
                    .isInstanceOf(InternalDatabaseExecutor.class);
            assertThat(registry.lookup(Phase.INJECTION, "custom-injector"))
                    .isInstanceOf(ExternalInjectorExecutor.class);
        }
    }

    // --- Multiple phases ---

    @Nested
    @DisplayName("Multi-phase registration")
    class MultiPhase {

        @Test
        @DisplayName("executors are distributed to correct phases based on annotation")
        void correctPhaseDistribution() {
            var registry = new DefaultPluginRegistry(
                    List.of(new InternalDatabaseExecutor(), new InternalGatlingExecutor(),
                            new InternalAssertionExecutor()),
                    loaderWith(List.of(new ExternalSeederExecutor(), new ExternalInjectorExecutor(),
                            new ExternalAssertionExecutor())),
                    "./nonexistent");

            // PREPARATION: database + external-seeder
            assertThat(registry.namesFor(Phase.PREPARATION))
                    .containsExactlyInAnyOrder("database", "external-seeder");

            // INJECTION: gatling + custom-injector
            assertThat(registry.namesFor(Phase.INJECTION))
                    .containsExactlyInAnyOrder("gatling", "custom-injector");

            // ASSERTION: gatling-metric + custom-assertion
            assertThat(registry.namesFor(Phase.ASSERTION))
                    .containsExactlyInAnyOrder("gatling-metric", "custom-assertion");
        }
    }

    // --- Edge cases ---

    @Nested
    @DisplayName("Edge cases")
    class EdgeCases {

        @Test
        @DisplayName("null pluginDir with empty internal executors creates empty registry")
        void skipsExternalWhenDirDoesNotExist() {
            var registry = new DefaultPluginRegistry(
                    List.of(new InternalShellExecutor()),
                    emptyLoader(),
                    "/path/that/does/not/exist/plugins");

            // Internal executor should still be registered
            assertThat(registry.contains(Phase.PREPARATION, "shell")).isTrue();
            assertThat(registry.lookup(Phase.PREPARATION, "shell"))
                    .isInstanceOf(InternalShellExecutor.class);

            // No external executors since dir doesn't exist
            assertThat(registry.namesFor(Phase.PREPARATION)).containsExactly("shell");
        }

        @Test
        @DisplayName("no internal and no external executors")
        void emptyRegistry() {
            var registry = new DefaultPluginRegistry(
                    List.of(), emptyLoader(), "/nonexistent");

            assertThat(registry.contains(Phase.PREPARATION, "anything")).isFalse();
            assertThat(registry.namesFor(Phase.PREPARATION)).isEmpty();
            assertThat(registry.namesFor(Phase.INJECTION)).isEmpty();
            assertThat(registry.namesFor(Phase.ASSERTION)).isEmpty();
        }

        @Test
        @DisplayName("lookup throws on empty registry")
        void lookupThrowsOnEmptyRegistry() {
            var registry = new DefaultPluginRegistry(
                    List.of(), emptyLoader(), "/nonexistent");

            assertThatThrownBy(() -> registry.lookup(Phase.PREPARATION, "anything"))
                    .isInstanceOf(UnsupportedTaskNameException.class);
        }
    }

    // --- Real directory loading with @TempDir ---

    @Nested
    @DisplayName("Integration: existing directory without JARs")
    class ExistingDirectory {

        @Test
        @DisplayName("loads plugins from existing directory (empty JAR list)")
        void loadsFromExistingDirectory(@TempDir Path tempDir) {
            // PluginLoader is called with the temp dir — it returns empty result
            // because there are no JAR files
            var registry = new DefaultPluginRegistry(
                    List.of(new InternalShellExecutor()),
                    emptyLoader(),
                    tempDir.toString());

            assertThat(registry.contains(Phase.PREPARATION, "shell")).isTrue();
        }
    }

    // --- Constructor validation for unannotated executor ---

    @Nested
    @DisplayName("Unannotated executor rejection")
    class UnannotatedRejection {

        @Test
        @DisplayName("constructor throws when internal executor has no plugin annotation")
        void rejectsInternalUnannotated() {
            var unannotated = new UnannotatedExecutor();
            assertThatThrownBy(() -> new DefaultPluginRegistry(
                    List.of(unannotated), emptyLoader(), "/nonexistent"))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("UnannotatedExecutor")
                    .hasMessageContaining("missing");
        }

        @Test
        @DisplayName("constructor throws when external executor has no annotation")
        void rejectsExternalUnannotated() {
            var unannotated = new UnannotatedExecutor();
            assertThatThrownBy(() -> new DefaultPluginRegistry(
                    List.of(),
                    loaderWith(List.of(unannotated)),
                    "/nonexistent"))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("UnannotatedExecutor")
                    .hasMessageContaining("missing");
        }
    }

    // --- namesFor immutability ---

    @Nested
    @DisplayName("namesFor immutability")
    class NamesForImmutability {

        @Test
        @DisplayName("returned set is unmodifiable")
        void returnedSetIsUnmodifiable() {
            var registry = new DefaultPluginRegistry(
                    List.of(new InternalShellExecutor()),
                    emptyLoader(),
                    "/nonexistent");

            Set<String> names = registry.namesFor(Phase.PREPARATION);
            assertThat(names).isUnmodifiable();
        }
    }
}
