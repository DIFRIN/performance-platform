package com.performance.platform.domain.execution;

import com.performance.platform.domain.id.ExecutionId;
import com.performance.platform.domain.id.ScenarioId;
import com.performance.platform.domain.task.TaskResult;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Contexte d'exécution strictement immuable, transmis uniquement côté orchestrateur.
 * Le store suit la structure {@code taskId → agentId → TaskResult}.
 * Toute modification passe par {@link #with(String, String, TaskResult)} qui
 * retourne une nouvelle instance (copy-on-write). Jamais de setter.
 *
 * <p>0 annotation framework — record Java pur.</p>
 */
public record ExecutionContext(
    ExecutionId executionId,
    ScenarioId scenarioId,
    Map<String, Map<String, TaskResult>> store
) {

    /**
     * Constructeur compact avec copie défensive profonde.
     * Chaque Map imbriquée est également copiée en non-mutable.
     */
    public ExecutionContext {
        Objects.requireNonNull(executionId, "executionId required");
        Objects.requireNonNull(scenarioId, "scenarioId required");
        if (store == null) {
            store = Map.of();
        } else {
            var deepCopy = new HashMap<String, Map<String, TaskResult>>(store.size());
            for (var entry : store.entrySet()) {
                deepCopy.put(entry.getKey(), Map.copyOf(entry.getValue()));
            }
            store = Map.copyOf(deepCopy);
        }
    }

    /**
     * Contexte initial vide pour le début d'une exécution.
     */
    public static ExecutionContext initial(ExecutionId id, ScenarioId scenarioId) {
        return new ExecutionContext(id, scenarioId, Map.of());
    }

    /**
     * Ajoute ou remplace le {@link TaskResult} d'un agent pour une task.
     * Copy-on-write : l'instance courante n'est jamais modifiée.
     *
     * @param taskId  identifiant de la task
     * @param agentId identifiant de l'agent
     * @param result  résultat de la tâche
     * @return nouvelle instance avec le résultat ajouté
     * @throws NullPointerException si un paramètre est null
     */
    public ExecutionContext with(String taskId, String agentId, TaskResult result) {
        Objects.requireNonNull(taskId, "taskId required");
        Objects.requireNonNull(agentId, "agentId required");
        Objects.requireNonNull(result, "result required");
        var newStore = new HashMap<>(this.store);
        var existingAgentResults = newStore.getOrDefault(taskId, Map.of());
        var newAgentResults = new HashMap<>(existingAgentResults);
        newAgentResults.put(agentId, result);
        newStore.put(taskId, Map.copyOf(newAgentResults));
        return new ExecutionContext(executionId, scenarioId, Map.copyOf(newStore));
    }

    /**
     * Récupère une valeur des outputs d'un {@link TaskResult} spécifique (taskId + agentId).
     *
     * @param taskId  identifiant de la task
     * @param agentId identifiant de l'agent
     * @param type    type attendu de la valeur
     * @param <T>     type de la valeur
     * @return la première valeur des outputs castable en {@code type}, ou {@link Optional#empty()}
     */
    @SuppressWarnings("unchecked")
    public <T> Optional<T> get(String taskId, String agentId, Class<T> type) {
        Objects.requireNonNull(taskId, "taskId required");
        Objects.requireNonNull(agentId, "agentId required");
        Objects.requireNonNull(type, "type required");
        var agentResults = store.get(taskId);
        if (agentResults == null) return Optional.empty();
        var result = agentResults.get(agentId);
        if (result == null) return Optional.empty();
        for (var value : result.outputs().values()) {
            if (type.isInstance(value)) {
                return Optional.of((T) value);
            }
        }
        return Optional.empty();
    }

    /**
     * Récupère la première valeur castable en {@code type}
     * parmi tous les agents ayant exécuté la task donnée.
     *
     * @param taskId identifiant de la task
     * @param type   type attendu de la valeur
     * @param <T>    type de la valeur
     * @return la première valeur trouvée, ou {@link Optional#empty()}
     */
    @SuppressWarnings("unchecked")
    public <T> Optional<T> getFirst(String taskId, Class<T> type) {
        Objects.requireNonNull(taskId, "taskId required");
        Objects.requireNonNull(type, "type required");
        var agentResults = store.get(taskId);
        if (agentResults == null) return Optional.empty();
        for (var result : agentResults.values()) {
            for (var value : result.outputs().values()) {
                if (type.isInstance(value)) {
                    return Optional.of((T) value);
                }
            }
        }
        return Optional.empty();
    }

    /**
     * Retourne tous les résultats par agent pour une task donnée.
     *
     * @param taskId identifiant de la task
     * @return map {@code agentId → TaskResult}, vide non-null si la task est absente
     */
    public Map<String, TaskResult> getAll(String taskId) {
        Objects.requireNonNull(taskId, "taskId required");
        var agentResults = store.get(taskId);
        if (agentResults == null) return Map.of();
        return agentResults; // déjà immuable via copie défensive du constructeur
    }
}
