package com.performance.platform.observability.listener;

import com.performance.platform.domain.event.PhaseCompleted;
import com.performance.platform.domain.event.ScenarioFinished;
import com.performance.platform.domain.event.TaskCompleted;
import com.performance.platform.domain.event.TaskFailed;
import com.performance.platform.observability.metrics.ExecutionMetrics;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Objects;

/**
 * Ecoute les domain events et alimente les metriques {@link ExecutionMetrics}.
 * <p>
 * Aucune logique metier — purement reactif. Chaque methode {@code @EventListener}
 * extrait les informations pertinentes de l'event et les transmet au port
 * {@link ExecutionMetrics}.
 * <p>
 * <b>Mapping event → metrique :</b>
 * <ul>
 *   <li>{@link TaskCompleted} → {@link ExecutionMetrics#recordTaskDuration}</li>
 *   <li>{@link TaskFailed} → {@link ExecutionMetrics#incrementTaskFailure}</li>
 *   <li>{@link PhaseCompleted} → {@link ExecutionMetrics#recordPhaseDuration}</li>
 *   <li>{@link ScenarioFinished} → {@link ExecutionMetrics#recordExecutionDuration}</li>
 * </ul>
 * <p>
 * Les spans OpenTelemetry sont gerees automatiquement par Micrometer Tracing
 * (Spring Boot auto-configuration) sans code supplementaire dans ce listener.
 * <p>
 * <b>CC-02:</b> Pipeline cohesif d'ecoute et delegation —
 * chaque methode {@code @EventListener} suit un flux identique :
 * validation rapide → extraction des champs de l'event → appel
 * de la metrique correspondante sur {@link ExecutionMetrics} → log structure.
 * Les 4 methodes sont indissociables car elles forment ensemble le
 * contrat "tout event domaine a une metrique".
 */
@Component
public class ObservabilityEventListener {

    private static final Logger log = LoggerFactory.getLogger(ObservabilityEventListener.class);

    private final ExecutionMetrics metrics;

    public ObservabilityEventListener(ExecutionMetrics metrics) {
        this.metrics = Objects.requireNonNull(metrics, "metrics must not be null");
    }

    // ---- Task events ----

    /**
     * Enregistre la duree d'une tache reussie.
     * <p>
     * Extrait le {@code taskName} du {@link com.performance.platform.domain.task.TaskResult}
     * pour le tagguer dans la metrique.
     */
    @EventListener
    public void on(TaskCompleted event) {
        Objects.requireNonNull(event, "event must not be null");
        String taskName = event.result().taskName();
        log.info("action=task_completed executionId={} taskId={} taskName={} durationMs={}",
                event.executionId().value(), event.taskId().value(), taskName,
                event.duration().toMillis());
        metrics.recordTaskDuration(event.taskId(), taskName, event.duration());
    }

    /**
     * Incremente le compteur d'echecs pour une tache.
     * <p>
     * Note : l'event {@link TaskFailed} ne porte pas le {@code taskName}.
     * On utilise {@code "unknown"} comme tag par defaut — le compteur
     * reste neanmoins incremente pour la visibilite operationnelle.
     */
    @EventListener
    public void on(TaskFailed event) {
        Objects.requireNonNull(event, "event must not be null");
        log.warn("action=task_failed executionId={} taskId={} error={} attempt={}",
                event.executionId().value(), event.taskId().value(),
                event.error(), event.attempt());
        metrics.incrementTaskFailure(event.taskId(), "unknown");
    }

    // ---- Phase event ----

    /**
     * Enregistre la duree d'une phase.
     * <p>
     * Note : l'event {@link PhaseCompleted} ne porte pas de {@code Duration}.
     * On utilise {@link Duration#ZERO} comme valeur par defaut — le vrai
     * calcul de duree de phase se fera via Observation/Tracing.
     */
    @EventListener
    public void on(PhaseCompleted event) {
        Objects.requireNonNull(event, "event must not be null");
        log.info("action=phase_completed executionId={} phase={} phaseStatus={}",
                event.executionId().value(), event.phase().name(), event.status().name());
        metrics.recordPhaseDuration(event.phase(), Duration.ZERO);
    }

    // ---- Scenario event ----

    /**
     * Enregistre la duree totale d'execution d'un scenario.
     */
    @EventListener
    public void on(ScenarioFinished event) {
        Objects.requireNonNull(event, "event must not be null");
        log.info("action=scenario_finished executionId={} scenarioId={} verdict={} durationMs={}",
                event.executionId().value(), event.scenarioId().value(),
                event.verdict().name(), event.duration().toMillis());
        metrics.recordExecutionDuration(event.executionId(), event.duration());
    }
}
