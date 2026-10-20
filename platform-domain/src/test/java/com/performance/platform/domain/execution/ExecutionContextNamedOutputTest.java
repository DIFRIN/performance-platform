package com.performance.platform.domain.execution;

import com.performance.platform.domain.id.ExecutionId;
import com.performance.platform.domain.id.ScenarioId;
import com.performance.platform.domain.id.TaskId;
import com.performance.platform.domain.task.TaskResult;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("ExecutionContext — named outputs & persistent store")
class ExecutionContextNamedOutputTest {

    private static final ExecutionId EXECUTION_ID = ExecutionId.generate();
    private static final ScenarioId SCENARIO_ID = ScenarioId.of("scenario-named-output");

    @Test
    @DisplayName("getOutput reads a named output instead of the first value of a type")
    void getOutputUsesTheName() {
        var result = TaskResult.success(TaskId.of("t1"), "inject", Duration.ZERO,
                Map.of("name", "alpha", "code", "beta"));
        var context = ExecutionContext.initial(EXECUTION_ID, SCENARIO_ID)
                .with("t1", "agent-1", result);

        assertEquals("alpha",
                context.getOutput("t1", "agent-1", "name", String.class).orElseThrow());
        assertEquals("beta",
                context.getOutput("t1", "agent-1", "code", String.class).orElseThrow());
        assertTrue(context.getOutput("t1", "agent-1", "unknown", String.class).isEmpty());
        assertTrue(context.getOutput("t1", "agent-1", "name", Integer.class).isEmpty());
    }

    @Test
    @DisplayName("getFirstOutput is deterministic across agents")
    void getFirstOutputIsDeterministic() {
        var context = ExecutionContext.initial(EXECUTION_ID, SCENARIO_ID)
                .with("t1", "agent-b", TaskResult.success(
                        TaskId.of("t1"), "inject", Duration.ZERO, Map.of("v", "from-b")))
                .with("t1", "agent-a", TaskResult.success(
                        TaskId.of("t1"), "inject", Duration.ZERO, Map.of("v", "from-a")));

        assertEquals("from-a", context.getFirstOutput("t1", "v", String.class).orElseThrow());
        for (int i = 0; i < 1_000; i++) {
            assertEquals("from-a", context.getFirstOutput("t1", "v", String.class).orElseThrow());
        }
    }

    @Test
    @DisplayName("with does not copy untouched task results")
    void withSharesUntouchedTaskMaps() {
        var context = ExecutionContext.initial(EXECUTION_ID, SCENARIO_ID)
                .with("t1", "agent-1", TaskResult.success(
                        TaskId.of("t1"), "prep", Duration.ZERO, Map.of("v", 1)))
                .with("t2", "agent-1", TaskResult.success(
                        TaskId.of("t2"), "prep", Duration.ZERO, Map.of("v", 2)));

        var next = context.with("t2", "agent-2", TaskResult.success(
                TaskId.of("t2"), "prep", Duration.ZERO, Map.of("v", 3)));

        assertSame(context.getAll("t1"), next.getAll("t1"));
        assertFalse(context.getAll("t2").containsKey("agent-2"));
        assertTrue(next.getAll("t2").containsKey("agent-1"));
        assertTrue(next.getAll("t2").containsKey("agent-2"));
    }

    @Test
    @DisplayName("store remains a read-only Map view")
    void storeIsReadOnly() {
        var context = ExecutionContext.initial(EXECUTION_ID, SCENARIO_ID)
                .with("t1", "agent-1", TaskResult.success(
                        TaskId.of("t1"), "prep", Duration.ZERO, Map.of()));

        assertEquals(1, context.store().size());
        assertTrue(context.store().get("t1").containsKey("agent-1"));
        assertNull(context.store().get("absent"));
        assertThrows(UnsupportedOperationException.class,
                () -> context.store().put("x", Map.of()));
    }
}
