# ISSUE-005 — ExecutionContext immuable + PartialExecutionContext

**PDR** : PDR-001
**Module** : `platform-domain`
**Statut** : WAITING
**Priorité** : P0
**Bloquée par** : ISSUE-001, ISSUE-004
**Estime** : M

---

## Objectif

Créer `ExecutionContext` strictement immuable (méthode `with()` uniquement) et
`PartialExecutionContext` (sous-ensemble transmis aux agents). Implémenter les accesseurs
`get/getFirst/getAll`.

## Fichiers à Créer

```
platform-domain/src/main/java/com/performance/platform/domain/execution/
  ├── ExecutionContext.java
  └── PartialExecutionContext.java

platform-domain/src/test/java/com/performance/platform/domain/execution/
  ├── ExecutionContextTest.java        — immutabilité, with() retourne nouvelle instance, get/getFirst/getAll
  └── PartialExecutionContextTest.java — get/getFirst
```

## Interfaces à Implémenter

```java
public record ExecutionContext(ExecutionId executionId, ScenarioId scenarioId, Map<String, Object> store) {
    public ExecutionContext { store = store == null ? Map.of() : Map.copyOf(store); }
    public static ExecutionContext initial(ExecutionId id, ScenarioId scenarioId) {
        return new ExecutionContext(id, scenarioId, Map.of());
    }
    public ExecutionContext with(String key, Object value) {
        var newStore = new HashMap<>(this.store);
        newStore.put(key, value);
        return new ExecutionContext(executionId, scenarioId, Map.copyOf(newStore));
    }
    public <T> Optional<T> get(String taskId, String agentId, Class<T> type) { /* store[taskId][agentId].outputs cast */ }
    public <T> Optional<T> getFirst(String taskId, Class<T> type) { /* premier résultat dispo */ }
    public Map<String, TaskResult> getAll(String taskId) { /* agentId → TaskResult, ou map vide */ }
}

public record PartialExecutionContext(ExecutionId executionId, ScenarioId scenarioId,
                                      Map<String, Map<String, Object>> store) {
    public PartialExecutionContext { store = store == null ? Map.of() : Map.copyOf(store); }
    public <T> Optional<T> get(String taskId, String agentId, Class<T> type) { /* ... */ }
    public <T> Optional<T> getFirst(String taskId, Class<T> type) { /* ... */ }
}
```

## Règles Spécifiques

- `store` de `ExecutionContext` : structure `taskId → Map<AgentId(String), TaskResult>`.
- `with()` NE MUTE JAMAIS l'instance courante — copie + `Map.copyOf`.
- `getFirst()` : retourne le premier `TaskResult.outputs` castable en `type`, sinon `Optional.empty()`.
- `getAll()` : retourne map vide si la task est absente (jamais null).
- Aucune méthode `setXxx`, aucun accès mutable au store.

## Critères de Done

- [ ] `mvn test -pl platform-domain -q` → 0 erreur
- [ ] `ctx.with("k", v) != ctx` (nouvelle instance) et `ctx` inchangé
- [ ] `getAll("absent")` retourne une map vide non-null
- [ ] Tentative de mutation du store retournée → `UnsupportedOperationException`
- [ ] `progress.md` mis à jour : ISSUE-005 → DONE
- [ ] `context/interfaces-registry.md` : `ExecutionContext`, `PartialExecutionContext` → STABLE
