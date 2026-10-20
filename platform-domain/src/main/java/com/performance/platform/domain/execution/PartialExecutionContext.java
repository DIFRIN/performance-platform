package com.performance.platform.domain.execution;

import com.performance.platform.domain.id.ExecutionId;
import com.performance.platform.domain.id.ScenarioId;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Sous-ensemble immuable de l'{@link ExecutionContext} transmis aux agents.
 * Contient uniquement les entrées déclarées dans {@code StepDefinition.requiredContexts}.
 * Le store suit la structure {@code taskId → agentId → Map<String,Object>}
 * (outputs nommes du TaskResult). Les anciens messages (valeur brute) restent
 * acceptes par les accesseurs historiques.
 *
 * <p>Expose les mêmes accesseurs que {@code ExecutionContext} pour compatibilité
 * avec les {@code TaskExecutor} existants. Pas de {@code getAll()} —
 * les agents n'ont pas besoin d'accéder aux métadonnées complètes.</p>
 *
 * <p>0 annotation framework.</p>
 */
public record PartialExecutionContext(
    ExecutionId executionId,
    ScenarioId scenarioId,
    Map<String, Map<String, Object>> store
) {

    /**
     * Constructeur compact avec copie défensive profonde.
     */
    public PartialExecutionContext {
        Objects.requireNonNull(executionId, "executionId required");
        Objects.requireNonNull(scenarioId, "scenarioId required");
        if (store == null) {
            store = Map.of();
        } else {
            var deepCopy = new HashMap<String, Map<String, Object>>(store.size());
            for (var entry : store.entrySet()) {
                deepCopy.put(entry.getKey(), Map.copyOf(entry.getValue()));
            }
            store = Map.copyOf(deepCopy);
        }
    }

    /**
     * Contexte partiel vide (aucune entrée requise).
     */
    public static PartialExecutionContext empty(ExecutionId id, ScenarioId scenarioId) {
        return new PartialExecutionContext(id, scenarioId, Map.of());
    }

    /**
     * Récupère la valeur pour une combinaison taskId + agentId.
     *
     * @param taskId  identifiant de la task
     * @param agentId identifiant de l'agent
     * @param type    type attendu de la valeur
     * @param <T>     type de la valeur
     * @return la valeur castée, ou {@link Optional#empty()} si absente ou type incompatible
     */
    @SuppressWarnings("unchecked")
    public <T> Optional<T> get(String taskId, String agentId, Class<T> type) {
        Objects.requireNonNull(taskId, "taskId required");
        Objects.requireNonNull(agentId, "agentId required");
        Objects.requireNonNull(type, "type required");
        var agentResults = store.get(taskId);
        if (agentResults == null) return Optional.empty();
        return firstMatching(agentResults.get(agentId), type);
    }

    /**
     * Recupere un output nomme pour une combinaison taskId + agentId.
     * <p>
     * Les messages recents transportent la map complete des outputs ; les
     * anciens messages (valeur brute) ne peuvent pas etre resolus par nom.
     *
     * @param taskId     identifiant de la task
     * @param agentId    identifiant de l'agent
     * @param outputName nom contractuel de l'output
     * @param type       type attendu
     * @return la valeur si presente et du bon type
     */
    public <T> Optional<T> getOutput(String taskId, String agentId,
                                     String outputName, Class<T> type) {
        Objects.requireNonNull(taskId, "taskId required");
        Objects.requireNonNull(agentId, "agentId required");
        Objects.requireNonNull(outputName, "outputName required");
        Objects.requireNonNull(type, "type required");
        var agentResults = store.get(taskId);
        if (agentResults == null) return Optional.empty();
        return namedMatching(agentResults.get(agentId), outputName, type);
    }

    /**
     * Recupere un output nomme du premier agent (ordre deterministe par agentId).
     *
     * @param taskId     identifiant de la task
     * @param outputName nom contractuel de l'output
     * @param type       type attendu
     * @return la premiere valeur trouvee, ou vide
     */
    public <T> Optional<T> getFirstOutput(String taskId, String outputName, Class<T> type) {
        Objects.requireNonNull(taskId, "taskId required");
        Objects.requireNonNull(outputName, "outputName required");
        Objects.requireNonNull(type, "type required");
        var agentResults = store.get(taskId);
        if (agentResults == null) return Optional.empty();
        return agentResults.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(entry -> namedMatching(entry.getValue(), outputName, type))
                .flatMap(Optional::stream)
                .findFirst();
    }

    /**
     * Récupère la première valeur castable en {@code type}
     * parmi tous les agents pour la task donnée.
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
        return agentResults.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(entry -> firstMatching(entry.getValue(), type))
                .flatMap(Optional::stream)
                .findFirst();
    }

    @SuppressWarnings("unchecked")
    private static <T> Optional<T> firstMatching(Object raw, Class<T> type) {
        if (raw instanceof Map<?, ?> outputs) {
            for (Object value : outputs.values()) {
                if (type.isInstance(value)) {
                    return Optional.of((T) value);
                }
            }
            return Optional.empty();
        }
        return type.isInstance(raw) ? Optional.of((T) raw) : Optional.empty();
    }

    private static <T> Optional<T> namedMatching(Object raw, String outputName, Class<T> type) {
        if (raw instanceof Map<?, ?> outputs) {
            Object value = outputs.get(outputName);
            return type.isInstance(value) ? Optional.of(type.cast(value)) : Optional.empty();
        }
        // Ancien format (valeur brute) : pas de nom d'output disponible.
        return Optional.empty();
    }
}
