package com.performance.platform.engine.remote;

import com.performance.platform.domain.execution.ExecutionContext;
import com.performance.platform.domain.execution.PartialExecutionContext;
import com.performance.platform.domain.id.ExecutionId;
import com.performance.platform.domain.id.ScenarioId;
import com.performance.platform.domain.id.TaskId;
import com.performance.platform.domain.task.TaskResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("PartialContextBuilder")
class PartialContextBuilderTest {

    private static final ExecutionId EXECUTION_ID = ExecutionId.generate();
    private static final ScenarioId SCENARIO_ID = ScenarioId.of("scenario-1");

    @Nested
    @DisplayName("build")
    class BuildTests {

        @Test
        @DisplayName("should extract single required key with single agent result")
        void singleKeySingleAgent() {
            ExecutionContext ctx = ExecutionContext.initial(EXECUTION_ID, SCENARIO_ID)
                    .with("login", "agent-1",
                            TaskResult.success(TaskId.of("login"), "login",
                                    Duration.ZERO, Map.of("token", "eyJ...")));

            PartialExecutionContext partial = PartialContextBuilder.build(ctx, Set.of("login"));

            assertThat(partial.executionId()).isEqualTo(EXECUTION_ID);
            assertThat(partial.scenarioId()).isEqualTo(SCENARIO_ID);
            assertThat(partial.get("login", "agent-1", String.class)).hasValue("eyJ...");
        }

        @Test
        @DisplayName("should extract multiple required keys")
        void multipleKeys() {
            ExecutionContext ctx = ExecutionContext.initial(EXECUTION_ID, SCENARIO_ID)
                    .with("A", "agent-1",
                            TaskResult.success(TaskId.of("A"), "A",
                                    Duration.ZERO, Map.of("out", "a-out")))
                    .with("B", "agent-1",
                            TaskResult.success(TaskId.of("B"), "B",
                                    Duration.ZERO, Map.of("out", "b-out")));

            PartialExecutionContext partial = PartialContextBuilder.build(ctx, Set.of("A", "B"));

            assertThat(partial.getFirst("A", String.class)).hasValue("a-out");
            assertThat(partial.getFirst("B", String.class)).hasValue("b-out");
        }

        @Test
        @DisplayName("should include multiple agent results for same task")
        void multiAgentResults() {
            ExecutionContext ctx = ExecutionContext.initial(EXECUTION_ID, SCENARIO_ID)
                    .with("inject", "agent-perf-01",
                            TaskResult.success(TaskId.of("inject"), "inject",
                                    Duration.ZERO, Map.of("latency", 120)))
                    .with("inject", "agent-perf-02",
                            TaskResult.success(TaskId.of("inject"), "inject",
                                    Duration.ZERO, Map.of("latency", 145)));

            PartialExecutionContext partial = PartialContextBuilder.build(ctx, Set.of("inject"));

            assertThat(partial.get("inject", "agent-perf-01", Integer.class)).hasValue(120);
            assertThat(partial.get("inject", "agent-perf-02", Integer.class)).hasValue(145);
        }

        @Test
        @DisplayName("should ignore keys not present in context")
        void missingKeyIgnored() {
            ExecutionContext ctx = ExecutionContext.initial(EXECUTION_ID, SCENARIO_ID);

            PartialExecutionContext partial = PartialContextBuilder.build(ctx, Set.of("missing"));

            assertThat(partial.store()).isEmpty();
            assertThat(partial.getFirst("missing", Object.class)).isEmpty();
        }

        @Test
        @DisplayName("should ignore tasks with empty outputs")
        void emptyOutputsIgnored() {
            ExecutionContext ctx = ExecutionContext.initial(EXECUTION_ID, SCENARIO_ID)
                    .with("noop", "agent-1",
                            TaskResult.success(TaskId.of("noop"), "noop",
                                    Duration.ZERO, Map.of()));

            PartialExecutionContext partial = PartialContextBuilder.build(ctx, Set.of("noop"));

            // Empty outputs — no entry in partial store
            assertThat(partial.store()).doesNotContainKey("noop");
        }

        @Test
        @DisplayName("should include failed task results")
        void failedTaskResults() {
            ExecutionContext ctx = ExecutionContext.initial(EXECUTION_ID, SCENARIO_ID)
                    .with("bad-task", "agent-1",
                            TaskResult.failed(TaskId.of("bad-task"), "bad-task",
                                    Duration.ZERO, "something went wrong", null));

            PartialExecutionContext partial = PartialContextBuilder.build(ctx, Set.of("bad-task"));

            // Failed tasks have empty outputs, so key should be absent
            assertThat(partial.store()).doesNotContainKey("bad-task");
        }

        @Test
        @DisplayName("should return empty context when requiredContextKeys is null")
        void nullRequiredKeys() {
            ExecutionContext ctx = ExecutionContext.initial(EXECUTION_ID, SCENARIO_ID)
                    .with("t1", "agent-1",
                            TaskResult.success(TaskId.of("t1"), "t1",
                                    Duration.ZERO, Map.of("v", 42)));

            PartialExecutionContext partial = PartialContextBuilder.build(ctx, null);

            assertThat(partial.store()).isEmpty();
            assertThat(partial.executionId()).isEqualTo(EXECUTION_ID);
        }

        @Test
        @DisplayName("should handle empty required keys set")
        void emptyKeys() {
            ExecutionContext ctx = ExecutionContext.initial(EXECUTION_ID, SCENARIO_ID)
                    .with("t1", "agent-1",
                            TaskResult.success(TaskId.of("t1"), "t1",
                                    Duration.ZERO, Map.of("v", 42)));

            PartialExecutionContext partial = PartialContextBuilder.build(ctx, Set.of());

            assertThat(partial.store()).isEmpty();
        }
    }
}
