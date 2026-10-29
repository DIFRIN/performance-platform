package com.performance.platform.domain.event;

import com.performance.platform.domain.id.ExecutionId;
import com.performance.platform.domain.id.SignalId;

import java.time.Instant;
import java.util.Objects;

/**
 * Signal ordonnant le restart d'un ou de tous les scenarios en cours.
 * <p>
 * Implante {@link AgentSignal} (interface scellee).
 * {@code executionId} est nullable : quand il est null, le signal est broadcast
 * a tous les scenarios en cours.
 * <p>
 * Record immuable, 0 annotation framework.
 *
 * @param executionId nullable — si null, broadcast a tous les scenarios
 * @param reason      raison du restart (ex: "ORCHESTRATOR_RESTART")
 */
public record ScenarioRestartSignal(SignalId id, ExecutionId executionId, String reason, Instant issuedAt)
        implements AgentSignal {

    public ScenarioRestartSignal {
        Objects.requireNonNull(id, "id required");
        // executionId nullable — broadcast a tous les scenarios
        Objects.requireNonNull(reason, "reason required");
        Objects.requireNonNull(issuedAt, "issuedAt required");
    }
}
