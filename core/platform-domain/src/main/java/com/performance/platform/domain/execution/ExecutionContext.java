package com.performance.platform.domain.execution;

import com.performance.platform.domain.id.ExecutionId;
import com.performance.platform.domain.id.ScenarioId;
import com.performance.platform.domain.task.TaskResult;

import java.util.AbstractMap;
import java.util.ArrayDeque;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Contexte d'execution strictement immuable, transmis uniquement cote orchestrateur.
 * Le store suit la structure {@code taskId → agentId → TaskResult}.
 * <p>
 * L'immuabilite est preservee sans deep-copy du store complet : {@link #with}
 * ajoute une couche persistante (O(profondeur du chemin)) au lieu de copier
 * toute la map imbriquee. Le snapshot lisible est materialise a la demande et
 * mis en cache par instance.
 * <p>
 * 0 annotation framework — record Java pur.
 */
public record ExecutionContext(
    ExecutionId executionId,
    ScenarioId scenarioId,
    Map<String, Map<String, TaskResult>> store
) {

    public ExecutionContext {
        Objects.requireNonNull(executionId, "executionId required");
        Objects.requireNonNull(scenarioId, "scenarioId required");
        if (store == null) {
            store = PersistentStore.empty();
        } else if (!(store instanceof PersistentStore)) {
            store = PersistentStore.of(store);
        }
    }

    /**
     * Contexte initial vide pour le debut d'une execution.
     */
    public static ExecutionContext initial(ExecutionId id, ScenarioId scenarioId) {
        return new ExecutionContext(id, scenarioId, PersistentStore.empty());
    }

    /**
     * Ajoute ou remplace le {@link TaskResult} d'un agent pour une task.
     * Copy-on-write persistant : l'instance courante n'est jamais modifiee et
     * les entrees inchangees ne sont pas copiees.
     *
     * @param taskId  identifiant de la task
     * @param agentId identifiant de l'agent
     * @param result  resultat de la tache
     * @return nouvelle instance avec le resultat ajoute
     * @throws NullPointerException si un parametre est null
     */
    public ExecutionContext with(String taskId, String agentId, TaskResult result) {
        Objects.requireNonNull(taskId, "taskId required");
        Objects.requireNonNull(agentId, "agentId required");
        Objects.requireNonNull(result, "result required");
        PersistentStore persistent = store instanceof PersistentStore p
                ? p
                : PersistentStore.of(store);
        return new ExecutionContext(executionId, scenarioId, persistent.with(taskId, agentId, result));
    }

    /**
     * Recupere l'output nomme d'un resultat precis.
     * <p>
     * Remplace le lookup type-erasé : la cle de sortie fait partie du contrat.
     *
     * @param taskId     identifiant de la task
     * @param agentId    identifiant de l'agent
     * @param outputName nom contractuel de l'output (ex: {@code p95Ms})
     * @param type       type attendu
     * @return la valeur si presente et du bon type
     */
    public <T> Optional<T> getOutput(String taskId, String agentId,
                                     String outputName, Class<T> type) {
        Objects.requireNonNull(taskId, "taskId required");
        Objects.requireNonNull(agentId, "agentId required");
        Objects.requireNonNull(outputName, "outputName required");
        Objects.requireNonNull(type, "type required");
        TaskResult result = getAll(taskId).get(agentId);
        if (result == null) {
            return Optional.empty();
        }
        Object value = result.outputs().get(outputName);
        return type.isInstance(value) ? Optional.of(type.cast(value)) : Optional.empty();
    }

    /**
     * Recupere l'output nomme du premier agent (ordre deterministe par agentId)
     * dont le resultat contient une valeur du type demande.
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
        return getAll(taskId).entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(entry -> entry.getValue().outputs().get(outputName))
                .filter(Objects::nonNull)
                .filter(type::isInstance)
                .map(type::cast)
                .findFirst();
    }

    /**
     * Retourne tous les resultats par agent pour une task donnee.
     *
     * @param taskId identifiant de la task
     * @return map {@code agentId → TaskResult}, vide non-null si la task est absente
     */
    public Map<String, TaskResult> getAll(String taskId) {
        Objects.requireNonNull(taskId, "taskId required");
        Map<String, TaskResult> agentResults = store.get(taskId);
        return agentResults == null ? Map.of() : agentResults;
    }

    // ==================== Persistent store ====================

    /**
     * Map persistante par couches : chaque {@link #with} ajoute une couche
     * contenant la map complete (immuable) de la task modifiee. Les tasks
     * inchangees ne sont jamais copiees. Le snapshot lisible est construit
     * paresseusement et mis en cache.
     */
    private static final class PersistentStore extends AbstractMap<String, Map<String, TaskResult>> {

        private final PersistentStore parent;
        private final String taskId;
        private final Map<String, TaskResult> taskResults;
        private volatile Map<String, Map<String, TaskResult>> snapshot;

        private PersistentStore(PersistentStore parent, String taskId,
                                Map<String, TaskResult> taskResults) {
            this.parent = parent;
            this.taskId = taskId;
            this.taskResults = taskResults;
        }

        static PersistentStore empty() {
            return new PersistentStore(null, null, null);
        }

        static PersistentStore of(Map<String, Map<String, TaskResult>> source) {
            PersistentStore current = empty();
            for (var entry : source.entrySet()) {
                current = new PersistentStore(current, entry.getKey(),
                        Map.copyOf(entry.getValue()));
            }
            return current;
        }

        PersistentStore with(String taskId, String agentId, TaskResult result) {
            Map<String, TaskResult> existing = taskResultsFor(taskId);
            var updated = new HashMap<>(existing);
            updated.put(agentId, result);
            return new PersistentStore(this, taskId, Map.copyOf(updated));
        }

        private Map<String, TaskResult> taskResultsFor(String taskId) {
            for (PersistentStore layer = this; layer.parent != null; layer = layer.parent) {
                if (taskId.equals(layer.taskId)) {
                    return layer.taskResults;
                }
            }
            return Map.of();
        }

        private Map<String, Map<String, TaskResult>> snapshot() {
            Map<String, Map<String, TaskResult>> current = snapshot;
            if (current != null) {
                return current;
            }
            var materialized = new HashMap<String, Map<String, TaskResult>>();
            Deque<PersistentStore> chain = new ArrayDeque<>();
            for (PersistentStore layer = this; layer.parent != null; layer = layer.parent) {
                chain.push(layer);
            }
            for (PersistentStore layer : chain) {
                materialized.put(layer.taskId, layer.taskResults);
            }
            current = Collections.unmodifiableMap(materialized);
            snapshot = current;
            return current;
        }

        @Override
        public Set<Entry<String, Map<String, TaskResult>>> entrySet() {
            return snapshot().entrySet();
        }

        @Override
        public Map<String, TaskResult> get(Object key) {
            return snapshot().get(key);
        }

        @Override
        public int size() {
            return snapshot().size();
        }

        @Override
        public boolean containsKey(Object key) {
            return snapshot().containsKey(key);
        }

        @Override
        public Set<String> keySet() {
            return snapshot().keySet();
        }

        @Override
        public Collection<Map<String, TaskResult>> values() {
            return snapshot().values();
        }
    }
}
