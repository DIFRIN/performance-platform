package com.performance.platform.domain.event;

import com.performance.platform.domain.id.ExecutionId;
import com.performance.platform.domain.id.TaskId;

import java.time.Instant;

/**
 * Événement de résultat d'assertion.
 */
public sealed interface AssertionEvent
        permits AssertionPassed, AssertionFailed {

    ExecutionId executionId();
    TaskId assertionId();
    Instant timestamp();
}
