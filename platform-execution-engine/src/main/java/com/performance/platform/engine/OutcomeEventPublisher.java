package com.performance.platform.engine;

import com.performance.platform.domain.id.ExecutionId;
import com.performance.platform.domain.scenario.Phase;
import com.performance.platform.engine.shared.DagPhaseExecutor;

import java.util.List;

/**
 * Publie les evenements de tache issus des outcomes d'une phase.
 * <p>
 * En LOCAL, les steps sont executes dans la JVM de l'orchestrateur :
 * l'engine publie {@code TaskCompleted}/{@code TaskFailed} avec l'agent
 * local. En DISTRIBUTED, ces evenements sont publies par
 * {@code TransportExecutionEventHandler} au fil de l'eau, avec l'identite
 * reelle de l'agent : l'implementation distribuee est un no-op.
 */
@FunctionalInterface
public interface OutcomeEventPublisher {

    void publish(ExecutionId executionId,
                 List<DagPhaseExecutor.StepOutcome> outcomes,
                 Phase phase);
}
