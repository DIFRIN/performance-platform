package com.performance.platform.observability.metrics;

import com.performance.platform.domain.id.ExecutionId;
import com.performance.platform.domain.id.TaskId;
import com.performance.platform.domain.scenario.Phase;

import java.time.Duration;

/**
 * Contrat pour l'enregistrement des metriques d'execution de la plateforme.
 * <p>
 * Expose 4 metriques obligatoires :
 * <ol>
 *   <li>{@code execution_duration} — duree totale d'une campagne</li>
 *   <li>{@code task_duration} — duree d'une tache individuelle</li>
 *   <li>{@code task_failures_total} — compteur d'echecs par taskName</li>
 *   <li>{@code phase_duration} — duree par phase (PREPARATION, INJECTION, ASSERTION)</li>
 * </ol>
 * <p>
 * L'implementation par defaut est {@link MicrometerExecutionMetrics} basee sur
 * Micrometer {@code MeterRegistry}.
 */
public interface ExecutionMetrics {

    /**
     * Enregistre la duree totale d'une execution.
     *
     * @param executionId l'identifiant de l'execution
     * @param duration    la duree totale
     */
    void recordExecutionDuration(ExecutionId executionId, Duration duration);

    /**
     * Enregistre la duree d'execution d'une tache.
     *
     * @param taskId   l'identifiant de la tache
     * @param taskName le nom de la tache (tag pour le filtrage)
     * @param duration la duree d'execution
     */
    void recordTaskDuration(TaskId taskId, String taskName, Duration duration);

    /**
     * Incremente le compteur d'echecs pour une tache donnee.
     *
     * @param taskId   l'identifiant de la tache
     * @param taskName le nom de la tache (tag pour le filtrage)
     */
    void incrementTaskFailure(TaskId taskId, String taskName);

    /**
     * Enregistre la duree d'une phase d'execution.
     *
     * @param phase    la phase (PREPARATION, INJECTION, ASSERTION)
     * @param duration la duree de la phase
     */
    void recordPhaseDuration(Phase phase, Duration duration);
}
