package com.performance.platform.plugin;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("ExecutorAvailability — contrat")
class ExecutorAvailabilityTest {

    @Test
    @DisplayName("expose uniquement canAcceptNewTask() sans annotation Spring")
    void contractShape() {
        assertTrue(ExecutorAvailability.class.isInterface());
        Method[] methods = ExecutorAvailability.class.getDeclaredMethods();
        assertEquals(1, methods.length);
        assertEquals("canAcceptNewTask", methods[0].getName());
        assertEquals(boolean.class, methods[0].getReturnType());
        assertTrue(Arrays.stream(ExecutorAvailability.class.getAnnotations())
                .noneMatch(a -> a.annotationType().getName().startsWith("org.springframework")));
    }

    @Test
    @DisplayName("TaskExecutor n'etend pas ExecutorAvailability")
    void taskExecutorUnchanged() {
        assertFalse(ExecutorAvailability.class.isAssignableFrom(TaskExecutor.class));
    }

    @Test
    @DisplayName("implementation avec limite de concurrence")
    void limitedImplementation() {
        var active = new AtomicInteger();
        ExecutorAvailability availability = () -> active.get() < 1;
        assertTrue(availability.canAcceptNewTask());
        active.incrementAndGet();
        assertFalse(availability.canAcceptNewTask());
    }
}
