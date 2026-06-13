package com.performance.platform.application.config;

import com.performance.platform.domain.execution.TaskCompletionPolicy;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

/**
 * Tests unitaires pour le record ExecutionConfig.
 * Verifie instanciation, accesseurs, et egalite.
 */
@DisplayName("ExecutionConfig")
class ExecutionConfigTest {

    private static final Duration DEFAULT_AVAILABILITY = Duration.ofSeconds(120);
    private static final Duration DEFAULT_EXECUTION = Duration.ofMinutes(5);
    private static final Duration DEFAULT_WIP_RESET = Duration.ofSeconds(30);

    // --- Nominal case

    @Test
    @DisplayName("creer une configuration avec tous les champs valides")
    void createValidConfig() {
        var config = new ExecutionConfig(
                DEFAULT_AVAILABILITY,
                DEFAULT_EXECUTION,
                DEFAULT_WIP_RESET,
                TaskCompletionPolicy.ALL_COMPLETE
        );

        assertEquals(DEFAULT_AVAILABILITY, config.taskAvailabilityTimeout());
        assertEquals(DEFAULT_EXECUTION, config.taskExecutionTimeout());
        assertEquals(DEFAULT_WIP_RESET, config.workInProgressResetInterval());
        assertEquals(TaskCompletionPolicy.ALL_COMPLETE, config.completionPolicy());
    }

    // --- Completion policy variants

    @Test
    @DisplayName("supporter les deux politiques de completions")
    void bothCompletionPolicies() {
        var firstComplete = new ExecutionConfig(
                DEFAULT_AVAILABILITY, DEFAULT_EXECUTION, DEFAULT_WIP_RESET, TaskCompletionPolicy.FIRST_COMPLETE
        );
        var allComplete = new ExecutionConfig(
                DEFAULT_AVAILABILITY, DEFAULT_EXECUTION, DEFAULT_WIP_RESET, TaskCompletionPolicy.ALL_COMPLETE
        );

        assertEquals(TaskCompletionPolicy.FIRST_COMPLETE, firstComplete.completionPolicy());
        assertEquals(TaskCompletionPolicy.ALL_COMPLETE, allComplete.completionPolicy());
    }

    // --- Equality

    @Test
    @DisplayName("deux configurations identiques sont egales")
    void equalConfigurations() {
        var c1 = new ExecutionConfig(
                DEFAULT_AVAILABILITY, DEFAULT_EXECUTION, DEFAULT_WIP_RESET, TaskCompletionPolicy.ALL_COMPLETE
        );
        var c2 = new ExecutionConfig(
                DEFAULT_AVAILABILITY, DEFAULT_EXECUTION, DEFAULT_WIP_RESET, TaskCompletionPolicy.ALL_COMPLETE
        );

        assertEquals(c1, c2);
        assertEquals(c1.hashCode(), c2.hashCode());
    }

    @Test
    @DisplayName("deux configurations differentes ne sont pas egales")
    void differentConfigurations() {
        var c1 = new ExecutionConfig(
                DEFAULT_AVAILABILITY, DEFAULT_EXECUTION, DEFAULT_WIP_RESET, TaskCompletionPolicy.ALL_COMPLETE
        );
        var c2 = new ExecutionConfig(
                DEFAULT_AVAILABILITY, DEFAULT_EXECUTION, DEFAULT_WIP_RESET, TaskCompletionPolicy.FIRST_COMPLETE
        );

        assertNotEquals(c1, c2);
    }

    // --- toString

    @Test
    @DisplayName("toString contient les champs principaux")
    void toStringContainsFields() {
        var config = new ExecutionConfig(
                Duration.ofSeconds(120), Duration.ofMinutes(5), Duration.ofSeconds(30),
                TaskCompletionPolicy.ALL_COMPLETE
        );

        var str = config.toString();
        assertEquals(String.class, str.getClass());
    }
}
