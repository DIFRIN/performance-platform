# ISSUE-157 — ExecutionLifecycleSignal Domain Type

**PDR** : PDR-038
**Module** : `platform-domain`
**Status** : WAITING
**Priorite** : P0 (bloquant PDR-037 et PDR-038)
**Bloquee par** : aucune
**Estime** : M (1-3h)

---

## Objectif

Creer le type domaine `ExecutionLifecycleSignal` implementant `AgentSignal`, l'enum `LifecycleAction` (START, STOP), et mettre a jour la clause `permits` de `AgentSignal`. Ce signal generaliste est envoye par l'engine aux agents avant (START) et apres (STOP) l'execution de chaque tache. Pour les assertions avec `linkedTo`, il delimite la fenetre de monitoring concurrente avec l'injection.

## Fichiers a Creer

```
platform-domain/src/main/java/com/performance/platform/domain/event/
  ├── LifecycleAction.java              — nouvel enum (START, STOP)
  └── ExecutionLifecycleSignal.java     — nouveau record implementant AgentSignal
```

## Fichiers a Modifier

```
platform-domain/src/main/java/com/performance/platform/domain/event/
  └── AgentSignal.java                  — ajouter ExecutionLifecycleSignal a la clause permits
```

## Interfaces a Implementer

> Copiees du PDR-038.

```java
// LifecycleAction enum
package com.performance.platform.domain.event;

public enum LifecycleAction {
    START,
    STOP
}
```

```java
// ExecutionLifecycleSignal record
package com.performance.platform.domain.event;

import com.performance.platform.domain.id.ExecutionId;
import com.performance.platform.domain.id.SignalId;
import com.performance.platform.domain.id.TaskId;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;

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

    public static final String PARAM_INTERVAL_SECONDS = "intervalSeconds";
    public static final String PARAM_STOP_BEHAVIOR = "stopBehavior";
    public static final String PARAM_GRACE_PERIOD_DURATION = "gracePeriodDuration";
    public static final String PARAM_TASK_NAME = "taskName";
    public static final String PARAM_PHASE = "phase";

    public static final String STOP_IMMEDIATE = "immediate";
    public static final String STOP_COMPLETE_CURRENT_CYCLE = "completeCurrentCycle";
    public static final String STOP_GRACE_PERIOD = "gracePeriod";

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
```

```java
// AgentSignal -- mise a jour de la clause permits
public sealed interface AgentSignal
        permits ScenarioRestartSignal, ExecutionLifecycleSignal {
    SignalId id();
    Instant issuedAt();
}
```

## Regles Specifiques

- `LifecycleAction` enum et `ExecutionLifecycleSignal` record sont dans le package `com.performance.platform.domain.event` -- meme package que `AgentSignal` et `ScenarioRestartSignal`.
- 0 annotation framework dans les deux classes (ArchUnit verifie).
- `ExecutionLifecycleSignal` implemente `AgentSignal` -- la methode `id()` retourne `SignalId`, `issuedAt()` retourne `Instant`.
- `ScenarioRestartSignal` n'est pas modifie. Il compile et fonctionne comme avant.
- Le constructeur compact fait `Map.copyOf(parameters)` (defensive copy).
- Les factories `start()` et `stop()` sont des convenience methods pour l'engine.
- La methode `stopBehavior()` extrait le comportement depuis parameters avec defaut `"immediate"`.

## Tests

Fichier a creer :
```
platform-domain/src/test/java/com/performance/platform/domain/event/
  └── ExecutionLifecycleSignalTest.java
```

Tests unitaires a ecrire (minimum) :
1. `shouldCreateWithStartAction` -- factory `start()` produit `LifecycleAction.START`
2. `shouldCreateWithStopAction` -- factory `stop()` produit `LifecycleAction.STOP`
3. `shouldRequireNonNullFields` -- assertThrows si id/executionId/taskId/action/issuedAt null
4. `shouldAcceptNullParametersAndReplaceWithEmptyMap` -- parameters null -> Map.of()
5. `shouldDefensiveCopyParameters` -- la Map passee en parametre ne peut pas muter l'interne
6. `shouldReturnImmediateForMissingStopBehavior` -- stopBehavior() -> "immediate" si absent
7. `shouldReturnImmediateForUnknownStopBehavior` -- stopBehavior() -> "immediate" si valeur inconnue
8. `shouldReturnCorrectStopBehavior` -- tester "immediate", "completeCurrentCycle", "gracePeriod"
9. `shouldHaveCorrectConstantValues` -- verifier les 5 PARAM_* et 3 STOP_* constantes
10. `shouldImplementAgentSignal` -- instanceof AgentSignal == true
11. `shouldBeRecordWithCorrectComponents` -- verifier que c'est bien un record avec les bons composants

## Critères de Done

- [ ] `LifecycleAction.java` compile dans `platform-domain` (0 erreur)
- [ ] `ExecutionLifecycleSignal.java` compile dans `platform-domain` (0 erreur)
- [ ] `AgentSignal.java` permits mis a jour, compile toujours
- [ ] `ScenarioRestartSignal.java` compile toujours (pas de breaking change)
- [ ] `mvn test -pl platform-domain -q` -> 0 erreur
- [ ] Tous les tests unitaires listes ci-dessus passent
- [ ] ArchUnit test (existants dans platform-domain) : 0 annotation framework dans `event`
- [ ] `.claude/workspace/progress.md` mis a jour : ISSUE-157 -> DONE
- [ ] `.claude/workspace/interfaces-registry.md` mis a jour : `LifecycleAction`, `ExecutionLifecycleSignal`, `AgentSignal` (permits clause)
