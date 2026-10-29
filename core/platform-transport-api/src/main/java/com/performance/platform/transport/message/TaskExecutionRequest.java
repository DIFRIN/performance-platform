package com.performance.platform.transport.message;

import com.performance.platform.domain.execution.PartialExecutionContext;
import com.performance.platform.domain.execution.RetryPolicy;
import com.performance.platform.domain.id.ExecutionId;
import com.performance.platform.domain.id.MessageId;
import com.performance.platform.domain.scenario.StepDefinition;

import java.time.Instant;
import java.util.Objects;

/**
 * Demande d'execution de task envoyee par l'orchestrateur aux agents (broadcast).
 * <p>
 * Remplace l'ancien {@code TaskMessage}. Pas de {@code targetAgentId} —
 * la selection se fait cote agent via {@code TaskSpecializationFilter}
 * (voir ADR-008).
 * <p>
 * Le champ {@code id} (MessageId) sert de cle d'idempotence cote agent.
 */
public record TaskExecutionRequest(
        MessageId id,
        ExecutionId executionId,
        StepDefinition step,
        PartialExecutionContext context,
        Instant dispatchedAt,
        RetryPolicy retryPolicy
) {
    public TaskExecutionRequest {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(executionId, "executionId must not be null");
        Objects.requireNonNull(step, "step must not be null");
        Objects.requireNonNull(context, "context must not be null");
        Objects.requireNonNull(dispatchedAt, "dispatchedAt must not be null");
        Objects.requireNonNull(retryPolicy, "retryPolicy must not be null");
    }
}
