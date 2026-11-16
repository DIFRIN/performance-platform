package com.performance.platform.domain.execution;

import com.performance.platform.domain.id.ExecutionId;
import com.performance.platform.domain.id.ScenarioId;
import com.performance.platform.domain.task.TaskResult;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ExecutionContextShareTest {

    private static ExecutionContext ctx() {
        return ExecutionContext.initial(ExecutionId.generate(), ScenarioId.of("s"));
    }

    @Test
    void defaultShareIsSingle() {
        assertEquals(GrantShare.single(), ctx().share());
        assertEquals(GrantShare.single(),
                new ExecutionContext(ExecutionId.generate(), ScenarioId.of("s"), null).share());
    }

    @Test
    void withShareIsCopyOnWrite() {
        ExecutionContext base = ctx();
        ExecutionContext shared = base.withShare(GrantShare.equal(1, 3));
        assertEquals(GrantShare.single(), base.share());
        assertEquals(GrantShare.equal(1, 3), shared.share());
        assertThrows(NullPointerException.class, () -> base.withShare(null));
    }

    @Test
    void withKeepsShare() {
        ExecutionContext shared = ctx().withShare(GrantShare.equal(2, 4));
        ExecutionContext next = shared.with("t", "a",
                TaskResult.success(com.performance.platform.domain.id.TaskId.of("t"), "n", Duration.ZERO, Map.of()));
        assertEquals(GrantShare.equal(2, 4), next.share());
    }
}
