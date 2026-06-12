# ISSUE-003 — Records Scenario / Step / LoadModel / RetryPolicy

**PDR** : PDR-001
**Module** : `platform-domain`
**Statut** : WAITING
**Priorité** : P0
**Bloquée par** : ISSUE-001, ISSUE-002
**Estime** : M

---

## Objectif

Créer les records de définition de scénario : `ScenarioDefinition`, `StepDefinition`
(remplace `TaskDefinition`), `LoadModel`, `RetryPolicy`. Tous immuables avec copies défensives.

## Fichiers à Créer

```
platform-domain/src/main/java/com/performance/platform/domain/scenario/
  ├── ScenarioDefinition.java
  └── StepDefinition.java
platform-domain/src/main/java/com/performance/platform/domain/injection/
  └── LoadModel.java
platform-domain/src/main/java/com/performance/platform/domain/execution/
  └── RetryPolicy.java

platform-domain/src/test/java/com/performance/platform/domain/scenario/
  ├── ScenarioDefinitionTest.java — copies défensives, collections null→vide
  └── StepDefinitionTest.java     — non-null id/taskName/phase
```

## Interfaces à Implémenter

```java
public record ScenarioDefinition(
    ScenarioId id, String name, String version, List<String> tags,
    Map<String, String> metadata, ExecutionMode executionMode,
    List<StepDefinition> steps, Map<String, LoadModel> loadModels
) {
    public ScenarioDefinition {
        Objects.requireNonNull(id, "id required");
        tags = tags == null ? List.of() : List.copyOf(tags);
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
        steps = steps == null ? List.of() : List.copyOf(steps);
        loadModels = loadModels == null ? Map.of() : Map.copyOf(loadModels);
    }
}

public record StepDefinition(
    TaskId id, String taskName, Phase phase, Map<String, Object> parameters,
    List<TaskId> dependsOn, List<String> requiredContexts, Duration timeout, RetryPolicy retryPolicy
) {
    public StepDefinition {
        Objects.requireNonNull(id, "id required");
        Objects.requireNonNull(taskName, "taskName required");
        Objects.requireNonNull(phase, "phase required");
        parameters = parameters == null ? Map.of() : Map.copyOf(parameters);
        dependsOn = dependsOn == null ? List.of() : List.copyOf(dependsOn);
        requiredContexts = requiredContexts == null ? List.of() : List.copyOf(requiredContexts);
    }
}

public record LoadModel(LoadModelType type, Map<String, Object> parameters) {
    public LoadModel {
        Objects.requireNonNull(type, "type required");
        parameters = parameters == null ? Map.of() : Map.copyOf(parameters);
    }
}

public record RetryPolicy(int maxAttempts, Duration initialDelay, double multiplier,
                          Duration maxDelay, Set<Class<? extends Exception>> retryableExceptions) {
    public static RetryPolicy defaults() {
        return new RetryPolicy(3, Duration.ofSeconds(1), 2.0, Duration.ofSeconds(30), Set.of());
    }
}
```

## Règles Spécifiques

- `taskName` est un `String` libre — JAMAIS un enum `TaskType`.
- `timeout` et `retryPolicy` peuvent être null (défauts gérés à l'exécution).
- Copies défensives via `List.copyOf` / `Map.copyOf` / `Set.copyOf`.

## Critères de Done

- [ ] `mvn test -pl platform-domain -q` → 0 erreur
- [ ] `new StepDefinition(id, null, phase, ...)` lève `NullPointerException`
- [ ] Modifier la liste source après construction ne modifie pas le record
- [ ] Aucune référence à `TaskDefinition` ou `TaskType`
- [ ] `progress.md` mis à jour : ISSUE-003 → DONE
- [ ] `context/interfaces-registry.md` : `StepDefinition`, `ScenarioDefinition`, `LoadModel` → STABLE
