# ISSUE-158 — Engine Lifecycle Signal Dispatch

**PDR** : PDR-037
**Module** : `platform-execution-engine`
**Status** : WAITING
**Priorite** : P0 (bloquant ISSUE-159)
**Bloquee par** : ISSUE-157 (ExecutionLifecycleSignal domain type)
**Estime** : M (1-3h)

---

## Objectif

Implementer le dispatch des signaux `ExecutionLifecycleSignal` (START/STOP) dans l'engine d'execution. L'engine envoie START avant l'execution de chaque task et STOP apres. Pour les assertions avec `linkedTo`, le START est envoye avant que l'injection liee ne commence, et le STOP est envoye apres la completion de l'injection, permettant un monitoring concurrent. En mode LOCAL, les signaux sont dispatches en memoire. En mode DISTRIBUTED, ils sont broadcastes via `transport.broadcastSignal()`.

## Fichiers a Creer

```
platform-execution-engine/src/main/java/com/performance/platform/engine/
  ├── ExecutionLifecycleDispatcher.java          — interface fonctionnelle de dispatch
  └── lifecycle/
      ├── LocalLifecycleDispatcher.java          — implementation LOCAL (appel LocalAgent)
      └── RemoteLifecycleDispatcher.java         — implementation DISTRIBUTED (appel transport.broadcastSignal)
```

## Fichiers a Modifier

```
platform-execution-engine/src/main/java/com/performance/platform/engine/
  ├── DagPhaseExecutor.java                      — injecter ExecutionLifecycleDispatcher, envoyer START/STOP autour de executeStep()
  ├── local/LocalExecutionEngine.java            — ajouter dispatchLifecycleSignal avant/autour de executePhase, coordonner linkedTo
  ├── local/ExecutionPlanBuilder.java            — peupler assertionIntervalSteps depuis linkedTo
  └── remote/RemoteExecutionEngine.java          — ajouter dispatchLifecycleSignal avant/autour de executePhase, coordonner linkedTo
```

```
platform-domain/src/main/java/com/performance/platform/domain/
  └── ExecutionPlan.java                         — ajouter champ List<ExecutionStep> assertionIntervalSteps
```

## Structure

### ExecutionLifecycleDispatcher

```java
package com.performance.platform.engine;

import com.performance.platform.domain.id.TaskId;

/**
 * Dispatche les signaux de cycle de vie START/STOP.
 * <p>
 * En LOCAL : appelle LocalAgent.onLifecycleSignal().
 * En DISTRIBUTED : appelle transport.broadcastSignal().
 */
@FunctionalInterface
public interface ExecutionLifecycleDispatcher {
    void dispatch(ExecutionLifecycleSignal signal);
}
```

### LocalLifecycleDispatcher

```java
package com.performance.platform.engine.lifecycle;

import com.performance.platform.agent.runtime.LocalAgent;
import com.performance.platform.domain.event.ExecutionLifecycleSignal;
import com.performance.platform.engine.ExecutionLifecycleDispatcher;

/**
 * Implementation LOCAL du dispatcher de signaux de cycle de vie.
 * Appelle LocalAgent.onLifecycleSignal() directement (meme JVM, pas de transport).
 */
class LocalLifecycleDispatcher implements ExecutionLifecycleDispatcher {
    private final LocalAgent localAgent;

    LocalLifecycleDispatcher(LocalAgent localAgent) {
        this.localAgent = localAgent;
    }

    @Override
    public void dispatch(ExecutionLifecycleSignal signal) {
        localAgent.onLifecycleSignal(signal);
    }
}
```

### RemoteLifecycleDispatcher

```java
package com.performance.platform.engine.lifecycle;

import com.performance.platform.domain.event.ExecutionLifecycleSignal;
import com.performance.platform.engine.ExecutionLifecycleDispatcher;
import com.performance.platform.transport.ExecutionTransport;

/**
 * Implementation DISTRIBUTED du dispatcher de signaux de cycle de vie.
 * Broadcast via transport.broadcastSignal().
 */
class RemoteLifecycleDispatcher implements ExecutionLifecycleDispatcher {
    private final ExecutionTransport transport;

    RemoteLifecycleDispatcher(ExecutionTransport transport) {
        this.transport = transport;
    }

    @Override
    public void dispatch(ExecutionLifecycleSignal signal) {
        transport.broadcastSignal(signal);
    }
}
```

### ExecutionPlan -- ajout assertionIntervalSteps

```java
public record ExecutionPlan(
    ExecutionId id,
    ScenarioId scenarioId,
    List<ExecutionStep> preparationSteps,
    List<ExecutionStep> injectionSteps,
    List<ExecutionStep> assertionSteps,              // assertions point-in-time
    List<ExecutionStep> assertionIntervalSteps,      // NOUVEAU -- assertions avec linkedTo
    ExecutionContext initialContext
) {
    // Constructeur compact : assertionIntervalSteps = List.copyOf() si non-null
}
```

### ExecutionPlanBuilder -- modification

Dans `DefaultExecutionPlanBuilder.build()` :
1. Apres avoir groupe les steps par phase
2. Pour chaque step ASSERTION, verifier si `step.parameters().get("linkedTo")` est present et non-null
3. Si oui, placer dans `assertionIntervalSteps` ; sinon dans `assertionSteps` (comportement existant)
4. Valider que `linkedTo` reference un stepId existant dans `injectionSteps`. Si non, throw `InvalidScenarioException`.

### DagPhaseExecutor -- modification

Dans `executeSingleStep()` :
1. Injecter `ExecutionLifecycleDispatcher` (nouveau parametre ou champ)
2. Au debut : `lifecycleDispatcher.dispatch(ExecutionLifecycleSignal.start(...))`
3. Apres execution : `lifecycleDispatcher.dispatch(ExecutionLifecycleSignal.stop(...))`
4. Dans le bloc catch aussi : envoyer STOP (toujours nettoyer)
5. Supprimer la branche conditionnelle `phase == Phase.ASSERTION`
6. Remplacer `executeAssertionStep()` et `executePreparationOrInjectionStep()` par `executeStep()` unifie
7. Supprimer `assertionResultToTaskResult()` (la conversion est faite par `AssertionExecutor.execute()`)

Le nouvel appel :
```java
// Au debut :
lifecycleDispatcher.dispatch(ExecutionLifecycleSignal.start(
    SignalId.generate(), context.executionId(), stepDef.id(),
    Map.of(
        ExecutionLifecycleSignal.PARAM_TASK_NAME, stepDef.taskName(),
        ExecutionLifecycleSignal.PARAM_PHASE, phase.name()
    )
));

// ... execution ...

// A la fin (dans finally ou apres try-catch) :
lifecycleDispatcher.dispatch(ExecutionLifecycleSignal.stop(
    SignalId.generate(), context.executionId(), stepDef.id(),
    Map.of(
        ExecutionLifecycleSignal.PARAM_TASK_NAME, stepDef.taskName(),
        ExecutionLifecycleSignal.PARAM_PHASE, phase.name()
    )
));
```

### LocalExecutionEngine -- modification

Ajouter la coordination `linkedTo` :

```java
// Apres executePhase(PREPARATION) :
// Pour chaque assertionIntervalStep :
//   Envoyer START avec les parametres du step (intervalSeconds, stopBehavior, etc.)
//   via lifecycleDispatcher

// Puis executePhase(INJECTION) -- les agents d'assertion samplent en parallele

// Apres executePhase(INJECTION) :
// Pour chaque assertionIntervalStep :
//   Envoyer STOP avec les parametres de stop behavior
//   Attendre TaskCompleted (via correlation tracker ou attente directe en LOCAL)

// Puis executePhase(ASSERTION) -- assertions point-in-time
```

### RemoteExecutionEngine -- modification

Memes modifications que `LocalExecutionEngine`, mais via `transport.broadcastSignal()`.

## Regles Specifiques

- Les factories `start()` et `stop()` de `ExecutionLifecycleSignal` sont utilisees.
- `SignalId.generate()` produit un UUID unique pour chaque signal.
- `DagPhaseExecutor` ne fait plus de distinction entre les phases pour le lookup d'executor.
- Le `LocalExecutionEngine` utilise `LocalLifecycleDispatcher` ; le `RemoteExecutionEngine` utilise `RemoteLifecycleDispatcher`.
- Les deux implementations de `ExecutionLifecycleDispatcher` doivent etre des beans Spring conditionnels (`@ConditionalOnProperty(name = "runtime.mode", havingValue = "LOCAL")` / `"DISTRIBUTED"`).
- Le builder `DefaultExecutionPlanBuilder` doit etre mis a jour pour `assertionIntervalSteps`.
- Les tests existants pour `ExecutionPlanBuilder` doivent etre mis a jour pour le nouveau champ.

## Tests

Fichiers a creer :
```
platform-execution-engine/src/test/java/com/performance/platform/engine/
  └── lifecycle/
      ├── LocalLifecycleDispatcherTest.java       — tests unitaires avec mock LocalAgent
      └── RemoteLifecycleDispatcherTest.java      — tests unitaires avec mock ExecutionTransport
```

Fichiers a modifier :
```
platform-execution-engine/src/test/java/com/performance/platform/engine/
  ├── DefaultExecutionPlanBuilderTest.java        — ajouter les tests pour linkedTo / assertionIntervalSteps
  ├── DagPhaseExecutorTest.java                   — verifier que START/STOP sont envoyes
  ├── LocalExecutionEngineTest.java               — verifier la coordination linkedTo
  └── RemoteExecutionEngineTest.java              — verifier la coordination linkedTo
```

Tests unitaires minimum :
1. `shouldDispatchStartSignalBeforeExecution` -- DagPhaseExecutor envoie START avant execute()
2. `shouldDispatchStopSignalAfterExecution` -- DagPhaseExecutor envoie STOP apres execute()
3. `shouldDispatchStopSignalOnException` -- DagPhaseExecutor envoie STOP meme si exception
4. `shouldPopulateAssertionIntervalStepsForLinkedTo` -- ExecutionPlanBuilder lit linkedTo
5. `shouldPlaceLinkedToAssertionsInIntervalSteps` -- steps avec linkedTo vont dans assertionIntervalSteps
6. `shouldPlaceNonLinkedToAssertionsInAssertionSteps` -- steps sans linkedTo restent dans assertionSteps
7. `shouldRejectLinkedToWithUnknownStepId` -- linkedTo reference injection inexistante -> exception
8. `shouldCoordinateLinkedToStartBeforeInjection` -- Local/Runtime engine envoie START avant injection
9. `shouldCoordinateLinkedToStopAfterInjection` -- Local/Runtime engine envoie STOP apres injection
10. `shouldDispatchStartStopForStandardTask` -- Task standard recoit START+STOP

## Critères de Done

- [ ] `ExecutionLifecycleDispatcher` interface compile
- [ ] `LocalLifecycleDispatcher` + bean Spring conditionnel `LOCAL`
- [ ] `RemoteLifecycleDispatcher` + bean Spring conditionnel `DISTRIBUTED`
- [ ] `ExecutionPlan` mis a jour avec `assertionIntervalSteps`
- [ ] `DefaultExecutionPlanBuilder` lit `linkedTo`, peuple `assertionIntervalSteps`
- [ ] `DagPhaseExecutor` utilise le chemin unifie (plus de `executeAssertionStep()` ou `executePreparationOrInjectionStep()`)
- [ ] `DagPhaseExecutor` envoie START/STOP autour de chaque task
- [ ] `LocalExecutionEngine` coordonne les assertions avec `linkedTo`
- [ ] `RemoteExecutionEngine` coordonne les assertions avec `linkedTo`
- [ ] `mvn test -pl platform-execution-engine -q` -> 0 erreur
- [ ] `.claude/workspace/progress.md` mis a jour : ISSUE-158 -> DONE
- [ ] `.claude/workspace/interfaces-registry.md` mis a jour : `ExecutionLifecycleDispatcher`, `LocalLifecycleDispatcher`, `RemoteLifecycleDispatcher`
