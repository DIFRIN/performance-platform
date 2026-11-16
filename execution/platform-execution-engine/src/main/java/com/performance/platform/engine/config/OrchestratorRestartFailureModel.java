package com.performance.platform.engine.config;

import com.performance.platform.application.ports.out.ExecutionRepository;
import com.performance.platform.domain.event.ScenarioFinished;
import com.performance.platform.domain.execution.ExecutionState;
import com.performance.platform.domain.execution.ExecutionStatus;
import com.performance.platform.domain.report.Verdict;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.SmartLifecycle;

import java.time.Duration;
import java.time.Instant;

/**
 * Modele de defaillance de l'orchestrateur unique (ADR-058).
 * <p>
 * Au demarrage, toute execution trouvee dans un etat non terminal est
 * marquee {@code FAILED} avec la raison {@code orchestrator restarted},
 * et un {@link ScenarioFinished} est publie. Aucune execution ne peut
 * rester {@code RUNNING} apres un redemarrage ; la reprise et la haute
 * disponibilite sont hors perimetre.
 */
public class OrchestratorRestartFailureModel implements SmartLifecycle {

    private static final Logger log = LoggerFactory.getLogger(OrchestratorRestartFailureModel.class);

    private final ExecutionRepository executionRepository;
    private final ApplicationEventPublisher eventPublisher;
    private volatile boolean running;

    public OrchestratorRestartFailureModel(ExecutionRepository executionRepository,
                                           ApplicationEventPublisher eventPublisher) {
        this.executionRepository = executionRepository;
        this.eventPublisher = eventPublisher;
    }

    @Override
    public void start() {
        var inFlight = executionRepository.findAll(Integer.MAX_VALUE).stream()
                .filter(state -> state.status() == ExecutionStatus.RUNNING
                        || state.status() == ExecutionStatus.STARTED)
                .toList();

        var now = Instant.now();
        for (ExecutionState state : inFlight) {
            var failed = new ExecutionState(
                    state.id(), state.scenarioId(), ExecutionStatus.FAILED,
                    state.phaseStatuses(), state.context(), state.startedAt(), now,
                    state.plannedTaskCount(), state.tasks(), state.scenarioYaml(),
                    state.scenarioDetails());
            executionRepository.save(failed);
            eventPublisher.publishEvent(new ScenarioFinished(
                    state.id(), state.scenarioId(), Verdict.FAILED,
                    Duration.between(state.startedAt(), now), now));
            log.warn("action=execution_failed_on_restart executionId={} scenarioId={}",
                    state.id().value(), state.scenarioId().value());
        }

        if (!inFlight.isEmpty()) {
            log.warn("action=orchestrator_restart_failure_model applied=true failedExecutions={}",
                    inFlight.size());
        }
        running = true;
    }

    @Override
    public void stop() {
        running = false;
    }

    @Override
    public boolean isRunning() {
        return running;
    }

    @Override
    public boolean isAutoStartup() {
        return true;
    }

    @Override
    public int getPhase() {
        return Integer.MIN_VALUE;
    }
}
