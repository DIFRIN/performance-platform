package com.performance.platform.observability.metrics;

import com.performance.platform.domain.id.ExecutionId;
import com.performance.platform.domain.id.TaskId;
import com.performance.platform.domain.scenario.Phase;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

/**
 * Implementation Micrometer de {@link ExecutionMetrics}.
 * <p>
 * Enregistre les 4 metriques obligatoires dans le {@link MeterRegistry} :
 * <ul>
 *   <li>{@link #executionTimer} — {@code execution_duration} (tag: executionId)</li>
 *   <li>{@link #taskTimer} — {@code task_duration} (tags: taskId, taskName)</li>
 *   <li>{@link #taskFailureCounter} — {@code task_failures_total} (tag: taskName)</li>
 *   <li>{@link #phaseTimer} — {@code phase_duration} (tag: phase)</li>
 * </ul>
 * <p>
 * Les metriques sont creees au moment de l'appel (pas au demarrage) pour eviter
 * de pre-enregistrer des combinaisons de tags infinies. Micrometer gere le
 * caching interne des metriques par identite (nom + tags).
 * <p>
 * <b>CC-02:</b> Pipeline cohesif d'enregistrement de metriques —
 * chaque methode publique (recordExecutionDuration, recordTaskDuration,
 * incrementTaskFailure, recordPhaseDuration) suit le meme flux :
 * validation des parametres → construction de la metrique Micrometer
 * via le registry → enregistrement. Les helpers de creation
 * ({@link #buildExecutionTimer}, {@link #buildTaskTimer},
 * {@link #buildPhaseTimer}) sont indissociables des methodes
 * publiques car chaque combinaison de tags est unique au contexte
 * d'appel. Extraire une portion isolee nuirait a la lisibilite
 * du pipeline d'enregistrement.</p>
 */
@Component
public class MicrometerExecutionMetrics implements ExecutionMetrics {

    private static final Logger log = LoggerFactory.getLogger(MicrometerExecutionMetrics.class);

    static final String METRIC_EXECUTION_DURATION = "execution_duration";
    static final String METRIC_TASK_DURATION = "task_duration";
    static final String METRIC_TASK_FAILURES_TOTAL = "task_failures_total";
    static final String METRIC_PHASE_DURATION = "phase_duration";

    static final String TAG_EXECUTION_ID = "executionId";
    static final String TAG_TASK_ID = "taskId";
    static final String TAG_TASK_NAME = "taskName";
    static final String TAG_PHASE = "phase";

    private final MeterRegistry registry;

    public MicrometerExecutionMetrics(MeterRegistry registry) {
        this.registry = Objects.requireNonNull(registry, "registry must not be null");
    }

    @Override
    public void recordExecutionDuration(ExecutionId executionId, Duration duration) {
        Objects.requireNonNull(executionId, "executionId must not be null");
        Objects.requireNonNull(duration, "duration must not be null");

        Timer timer = buildExecutionTimer(executionId);
        timer.record(duration.toMillis(), TimeUnit.MILLISECONDS);
        log.debug("action=record_execution_duration executionId={} durationMs={}",
                executionId.value(), duration.toMillis());
    }

    @Override
    public void recordTaskDuration(TaskId taskId, String taskName, Duration duration) {
        Objects.requireNonNull(taskId, "taskId must not be null");
        Objects.requireNonNull(taskName, "taskName must not be null");
        Objects.requireNonNull(duration, "duration must not be null");

        Timer timer = buildTaskTimer(taskId, taskName);
        timer.record(duration.toMillis(), TimeUnit.MILLISECONDS);
        log.debug("action=record_task_duration taskId={} taskName={} durationMs={}",
                taskId.value(), taskName, duration.toMillis());
    }

    @Override
    public void incrementTaskFailure(TaskId taskId, String taskName) {
        Objects.requireNonNull(taskId, "taskId must not be null");
        Objects.requireNonNull(taskName, "taskName must not be null");

        var counter = Counter.builder(METRIC_TASK_FAILURES_TOTAL)
                .tag(TAG_TASK_NAME, taskName)
                .description("Total task failures")
                .register(registry);
        counter.increment();
        log.debug("action=increment_task_failure taskId={} taskName={}",
                taskId.value(), taskName);
    }

    @Override
    public void recordPhaseDuration(Phase phase, Duration duration) {
        Objects.requireNonNull(phase, "phase must not be null");
        Objects.requireNonNull(duration, "duration must not be null");

        Timer timer = buildPhaseTimer(phase);
        timer.record(duration.toMillis(), TimeUnit.MILLISECONDS);
        log.debug("action=record_phase_duration phase={} durationMs={}",
                phase.name(), duration.toMillis());
    }

    // ---- Timer builders (package-private for test access) ----

    Timer buildExecutionTimer(ExecutionId executionId) {
        return Timer.builder(METRIC_EXECUTION_DURATION)
                .tag(TAG_EXECUTION_ID, executionId.value())
                .description("Total execution duration")
                .register(registry);
    }

    Timer buildTaskTimer(TaskId taskId, String taskName) {
        return Timer.builder(METRIC_TASK_DURATION)
                .tag(TAG_TASK_ID, taskId.value())
                .tag(TAG_TASK_NAME, taskName)
                .description("Task execution duration")
                .register(registry);
    }

    Timer buildPhaseTimer(Phase phase) {
        return Timer.builder(METRIC_PHASE_DURATION)
                .tag(TAG_PHASE, phase.name())
                .description("Phase duration")
                .register(registry);
    }
}
