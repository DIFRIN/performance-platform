package com.performance.platform.engine.local;

import com.performance.platform.application.ports.out.ExecutionRepository;
import com.performance.platform.domain.event.TaskCompleted;
import com.performance.platform.domain.event.TaskFailed;
import com.performance.platform.domain.id.AgentId;
import com.performance.platform.domain.id.ExecutionId;
import com.performance.platform.domain.scenario.Phase;
import com.performance.platform.domain.task.TaskStatus;
import com.performance.platform.engine.OutcomeEventPublisher;
import com.performance.platform.engine.shared.DagPhaseExecutor;

import org.springframework.context.ApplicationEventPublisher;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * Publication des evenements de tache en mode LOCAL : l'agent local
 * execute le step dans la JVM de l'orchestrateur.
 */
public class LocalOutcomeEventPublisher implements OutcomeEventPublisher {

    private final ApplicationEventPublisher eventPublisher;
    private final ExecutionRepository executionRepository;

    public LocalOutcomeEventPublisher(ApplicationEventPublisher eventPublisher,
                                      ExecutionRepository executionRepository) {
        this.eventPublisher = Objects.requireNonNull(eventPublisher, "eventPublisher must not be null");
        this.executionRepository = Objects.requireNonNull(executionRepository,
                "executionRepository must not be null");
    }

    @Override
    public void publish(ExecutionId executionId,
                        List<DagPhaseExecutor.StepOutcome> outcomes,
                        Phase phase) {
        var agentId = AgentId.of(DagPhaseExecutor.DEFAULT_AGENT);
        var now = Instant.now();

        for (var outcome : outcomes) {
            // En LOCAL, aucun TransportExecutionEventHandler ne persiste les
            // resultats : l'engine les enregistre pour le reporting/progress.
            executionRepository.saveTaskResult(
                    executionId, outcome.taskId(), agentId, outcome.result());

            if (outcome.result().isSuccess() || outcome.result().status() == TaskStatus.SKIPPED) {
                eventPublisher.publishEvent(new TaskCompleted(
                        executionId, outcome.taskId(), agentId,
                        outcome.result(), outcome.result().duration(), now));
            } else {
                eventPublisher.publishEvent(new TaskFailed(
                        executionId, outcome.taskId(), agentId,
                        outcome.result().errorMessage() != null
                                ? outcome.result().errorMessage() : "failed",
                        1, now));
            }
        }
    }
}
