package com.performance.platform.domain.scenario;

import com.performance.platform.domain.execution.RetryPolicy;
import com.performance.platform.domain.id.TaskId;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Définition d'une étape dans un scénario (ADR-053, ADR-054, ADR-055).
 * <p>
 * Deux sortes d'étapes existent et la différence est explicite :
 * <ul>
 *   <li>une <b>action step</b> déclare {@code phase} et s'exécute jusqu'au bout ;</li>
 *   <li>une <b>service step</b> déclare {@code during} et a une durée de vie :
 *       démarrée, maintenue, arrêtée.</li>
 * </ul>
 * {@code ref} est une référence de première classe vers une autre étape (ADR-053),
 * {@code fanOut} détermine le nombre d'agents exécutants (ADR-055).
 * Record immuable — copies défensives sur toutes les collections.
 * 0 annotation framework.
 */
public record StepDefinition(
    TaskId id,
    String taskName,
    Phase phase,
    Map<String, Object> parameters,
    List<TaskId> dependsOn,
    TaskId ref,
    ServiceScope during,
    Duration drain,
    FanOut fanOut,
    List<String> requiredContexts,
    Duration timeout,
    RetryPolicy retryPolicy,
    int minAgents,
    AgentLossPolicy onAgentLoss
) {

    public StepDefinition {
        Objects.requireNonNull(id, "id required");
        Objects.requireNonNull(taskName, "taskName required");
        if (during == null && phase == null) {
            throw new IllegalArgumentException("phase is required for an action step");
        }
        if (during != null && phase != null) {
            throw new IllegalArgumentException("a service step declares during, not phase");
        }
        parameters = parameters == null ? Map.of() : Map.copyOf(parameters);
        dependsOn = dependsOn == null ? List.of() : List.copyOf(dependsOn);
        requiredContexts = requiredContexts == null ? List.of() : List.copyOf(requiredContexts);
        drain = drain == null ? Duration.ZERO : drain;
        if (drain.isNegative()) {
            throw new IllegalArgumentException("drain must not be negative");
        }
        fanOut = fanOut == null ? FanOut.ONE : fanOut;
        if (minAgents < 1) {
            throw new IllegalArgumentException("minAgents must be >= 1");
        }
        onAgentLoss = onAgentLoss == null ? AgentLossPolicy.ABORT : onAgentLoss;
        // timeout peut être null (défaut géré à l'exécution)
        // retryPolicy peut être null (pas de retry)
    }

    /**
     * Ancienne signature : {@code minAgents = 1}, {@code onAgentLoss = ABORT}.
     */
    public StepDefinition(TaskId id, String taskName, Phase phase, Map<String, Object> parameters,
                          List<TaskId> dependsOn, TaskId ref, ServiceScope during, Duration drain,
                          FanOut fanOut, List<String> requiredContexts, Duration timeout,
                          RetryPolicy retryPolicy) {
        this(id, taskName, phase, parameters, dependsOn, ref, during, drain, fanOut,
                requiredContexts, timeout, retryPolicy, 1, AgentLossPolicy.ABORT);
    }

    /**
     * Raccourci pour une action step sans dépendance, référence, service ni fan-out.
     */
    public StepDefinition(TaskId id, String taskName, Phase phase, Map<String, Object> parameters,
                          List<TaskId> dependsOn, List<String> requiredContexts,
                          Duration timeout, RetryPolicy retryPolicy) {
        this(id, taskName, phase, parameters, dependsOn, null, null, null, FanOut.ONE,
                requiredContexts, timeout, retryPolicy, 1, AgentLossPolicy.ABORT);
    }

    /** Une étape de service ne participe pas à l'ordonnancement des phases. */
    public boolean hasLifetime() {
        return during != null;
    }

    /**
     * Clés de contexte effectives (ADR-053) : l'override explicite s'il existe,
     * sinon {@code dependsOn} plus {@code ref}.
     */
    public List<String> resolvedRequiredContexts() {
        if (!requiredContexts.isEmpty()) {
            return requiredContexts;
        }
        var derived = new ArrayList<String>(dependsOn.size() + 1);
        for (TaskId dependency : dependsOn) {
            derived.add(dependency.value());
        }
        if (ref != null && !derived.contains(ref.value())) {
            derived.add(ref.value());
        }
        return List.copyOf(derived);
    }
}
