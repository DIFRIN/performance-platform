package com.performance.platform.domain.event;

import com.performance.platform.domain.id.ExecutionId;
import com.performance.platform.domain.id.SignalId;
import com.performance.platform.domain.id.TaskId;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;

/**
 * Signal de cycle de vie envoye par l'orchestrateur aux agents avant (START)
 * et apres (STOP) l'execution de chaque tache.
 * <p>
 * Pour les assertions avec {@code linkedTo}, delimite la fenetre de monitoring
 * concurrente avec l'injection.
 * <p>
 * Record immuable implementant {@link AgentSignal}, 0 annotation framework.
 *
 * @param id          identifiant unique du signal
 * @param executionId identifiant de l'execution concernee
 * @param taskId      identifiant de la tache concernee
 * @param action      START ou STOP
 * @param parameters  parametres optionnels (stopBehavior, intervalSeconds, etc.)
 * @param issuedAt    instant d'emission du signal
 */
public record ExecutionLifecycleSignal(
        SignalId id,
        ExecutionId executionId,
        TaskId taskId,
        LifecycleAction action,
        Map<String, Object> parameters,
        Instant issuedAt
) implements AgentSignal {

    public ExecutionLifecycleSignal {
        Objects.requireNonNull(id, "id required");
        Objects.requireNonNull(executionId, "executionId required");
        Objects.requireNonNull(taskId, "taskId required");
        Objects.requireNonNull(action, "action required");
        Objects.requireNonNull(issuedAt, "issuedAt required");
        parameters = parameters == null ? Map.of() : Map.copyOf(parameters);
    }

    // ─── Parameter Keys ───────────────────────────────────────────────

    public static final String PARAM_INTERVAL_SECONDS = "intervalSeconds";
    public static final String PARAM_STOP_BEHAVIOR = "stopBehavior";
    public static final String PARAM_GRACE_PERIOD_DURATION = "gracePeriodDuration";
    public static final String PARAM_TASK_NAME = "taskName";
    public static final String PARAM_PHASE = "phase";

    // ─── Stop Behavior Values ──────────────────────────────────────────

    public static final String STOP_IMMEDIATE = "immediate";
    public static final String STOP_COMPLETE_CURRENT_CYCLE = "completeCurrentCycle";
    public static final String STOP_GRACE_PERIOD = "gracePeriod";

    // ─── Factory Methods ───────────────────────────────────────────────

    /**
     * Cree un signal START avec l'instant courant.
     */
    public static ExecutionLifecycleSignal start(
            SignalId signalId,
            ExecutionId executionId,
            TaskId taskId,
            Map<String, Object> parameters
    ) {
        return new ExecutionLifecycleSignal(
                signalId, executionId, taskId,
                LifecycleAction.START, parameters, Instant.now()
        );
    }

    /**
     * Cree un signal STOP avec l'instant courant.
     */
    public static ExecutionLifecycleSignal stop(
            SignalId signalId,
            ExecutionId executionId,
            TaskId taskId,
            Map<String, Object> parameters
    ) {
        return new ExecutionLifecycleSignal(
                signalId, executionId, taskId,
                LifecycleAction.STOP, parameters, Instant.now()
        );
    }

    // ─── Derived Accessor ─────────────────────────────────────────────

    /**
     * Extrait le comportement d'arret depuis les parametres.
     * Valeurs reconnues : {@code "immediate"}, {@code "completeCurrentCycle"}, {@code "gracePeriod"}.
     * Defaut : {@code "immediate"} si absent ou valeur inconnue.
     */
    public String stopBehavior() {
        Object val = parameters.get(PARAM_STOP_BEHAVIOR);
        if (val instanceof String s) {
            return switch (s) {
                case STOP_IMMEDIATE, STOP_COMPLETE_CURRENT_CYCLE, STOP_GRACE_PERIOD -> s;
                default -> STOP_IMMEDIATE;
            };
        }
        return STOP_IMMEDIATE;
    }
}
