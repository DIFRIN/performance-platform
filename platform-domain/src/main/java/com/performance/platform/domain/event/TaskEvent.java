package com.performance.platform.domain.event;

import com.performance.platform.domain.id.ExecutionId;
import com.performance.platform.domain.id.TaskId;

import java.time.Instant;

/**
 * Événement lié au cycle de vie d'une tâche.
 */
public sealed interface TaskEvent
        permits TaskStarted, TaskCompleted, TaskFailed, TaskRetried,
                TaskDispatched, TaskClaimedByAgent, TaskWorkInProgress {

    ExecutionId executionId();
    TaskId taskId();
    Instant timestamp();
}
