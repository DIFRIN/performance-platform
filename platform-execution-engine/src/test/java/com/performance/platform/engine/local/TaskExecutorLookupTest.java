package com.performance.platform.engine.local;

import com.performance.platform.plugin.AssertionExecutor;
import com.performance.platform.plugin.TaskExecutor;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;

/**
 * Tests unitaires pour la methode default {@link TaskExecutorLookup#findAssertionExecutor(String)}.
 */
class TaskExecutorLookupTest {

    @Test
    @DisplayName("findAssertionExecutor should delegate to findTaskExecutor and cast when executor is AssertionExecutor")
    void shouldReturnAssertionExecutorWhenTaskExecutorIsAssertionExecutor() {
        AssertionExecutor assertionExecutor = mock(AssertionExecutor.class);
        TaskExecutorLookup lookup = new TaskExecutorLookup() {
            @Override
            public TaskExecutor findTaskExecutor(String taskName) {
                return assertionExecutor;
            }
        };

        AssertionExecutor result = lookup.findAssertionExecutor("gatling-metric");

        assertNotNull(result);
        assertSame(assertionExecutor, result);
    }

    @Test
    @DisplayName("findAssertionExecutor should return null when executor is not an AssertionExecutor")
    void shouldReturnNullWhenTaskExecutorIsNotAssertionExecutor() {
        TaskExecutor plainExecutor = mock(TaskExecutor.class);
        TaskExecutorLookup lookup = new TaskExecutorLookup() {
            @Override
            public TaskExecutor findTaskExecutor(String taskName) {
                return plainExecutor;
            }
        };

        AssertionExecutor result = lookup.findAssertionExecutor("performance_test");

        assertNull(result);
    }
}
