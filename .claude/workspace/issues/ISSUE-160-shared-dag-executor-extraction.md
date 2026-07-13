# ISSUE-160 — Shared DAG Executor Extraction (StepDispatcher + LocalStepDispatcher)

**PDR** : PDR-039
**Module** : `platform-execution-engine`
**Statut** : DONE
**Priorite** : P1 (refactor structurel, pre-requis pour ISSUE-161)
**Bloquee par** : —
**Estime** : M (2-3h)

---

## Objectif

Déplacer `DagPhaseExecutor` du package `engine/local/` vers `engine/shared/` et introduire l'interface `StepDispatcher` pour abstraire la stratégie d'exécution d'un step. Implémenter `LocalStepDispatcher` qui préserve le comportement local existant (appel direct executor + retry).

## Fichiers à Créer

```
platform-execution-engine/src/main/java/com/performance/platform/engine/shared/
  ├── DagPhaseExecutor.java        — déplacé depuis engine/local/ (modifié : accepte StepDispatcher)
  └── StepDispatcher.java          — NOUVEAU: interface fonctionnelle

platform-execution-engine/src/main/java/com/performance/platform/engine/local/
  └── LocalStepDispatcher.java     — NOUVEAU: implémentation locale du StepDispatcher
```

## Fichiers à Modifier

```
platform-execution-engine/src/main/java/com/performance/platform/engine/local/
  ├── DagPhaseExecutor.java        — SUPPRIMÉ (déplacé vers shared/)
  └── LocalExecutionEngine.java    — utiliser DagPhaseExecutor depuis shared/ + injecter LocalStepDispatcher

platform-execution-engine/src/test/java/com/performance/platform/engine/local/
  └── DagPhaseExecutorTest.java    — déplacer vers shared/ (ou adapter les imports)
```

## Structure

### StepDispatcher

```java
package com.performance.platform.engine.shared;

import com.performance.platform.domain.execution.ExecutionContext;
import com.performance.platform.domain.execution.ExecutionStep;
import com.performance.platform.domain.scenario.Phase;
import com.performance.platform.domain.task.TaskResult;

/**
 * Abstrait la stratégie d'exécution d'un step.
 * En LOCAL : appel direct à l'executor.
 * En DISTRIBUTED : dispatch via transport + attente completion.
 */
@FunctionalInterface
public interface StepDispatcher {
    TaskResult dispatch(ExecutionStep execStep, ExecutionContext context, Phase phase);
}
```

### DagPhaseExecutor (modifications)

Le `DagPhaseExecutor` déplacé ne change PAS de logique métier. Les seuls changements :

1. Package : `com.performance.platform.engine.shared`
2. Constructeur : accepte `StepDispatcher` au lieu de `TaskExecutorLookup` + `RetryExecutor`
3. `executePhase()` : le paramètre `TaskExecutorLookup lookup` est remplacé par `StepDispatcher dispatcher`
4. `executeLevel()` : appelle `dispatcher.dispatch(step, context, phase)` au lieu de créer un `executeSingleStep()` interne
5. Suppression de `executeSingleStep()`, `executePreparationOrInjectionStep()`, `executeAssertionStep()`, `assertionResultToTaskResult()` — tout ça devient la responsabilité du `StepDispatcher`
6. Suppression du champ `RetryExecutor retryExecutor`

L'algorithme DAG (`groupStepsByLevel`, `classifySteps`, `allDependenciesSatisfied`, `executeLevel` avec VThreads, `publishTaskResult`, `publishTaskSkipped`) reste IDENTIQUE.

### LocalStepDispatcher

```java
package com.performance.platform.engine.local;

import com.performance.platform.domain.execution.ExecutionContext;
import com.performance.platform.domain.execution.ExecutionStep;
import com.performance.platform.domain.scenario.Phase;
import com.performance.platform.domain.scenario.StepDefinition;
import com.performance.platform.domain.task.TaskResult;
import com.performance.platform.domain.task.TaskStatus;
import com.performance.platform.engine.retry.RetryExecutor;
import com.performance.platform.engine.shared.StepDispatcher;
import com.performance.platform.plugin.AssertionExecutor;
import com.performance.platform.plugin.TaskExecutor;

import java.time.Duration;
import java.time.Instant;

/**
 * Implémentation locale du {@link StepDispatcher}.
 * Appelle l'executor directement (mémoire partagée JVM) avec retry.
 */
public class LocalStepDispatcher implements StepDispatcher {

    private final TaskExecutorLookup lookup;
    private final RetryExecutor retryExecutor;

    public LocalStepDispatcher(TaskExecutorLookup lookup, RetryExecutor retryExecutor) {
        this.lookup = lookup;
        this.retryExecutor = retryExecutor;
    }

    @Override
    public TaskResult dispatch(ExecutionStep execStep, ExecutionContext context, Phase phase) {
        StepDefinition stepDef = execStep.step();
        var policy = stepDef.retryPolicy() != null
                ? stepDef.retryPolicy()
                : com.performance.platform.domain.execution.RetryPolicy.defaults();

        var start = Instant.now();

        try {
            TaskResult result;
            if (phase == Phase.ASSERTION) {
                result = executeAssertion(stepDef, context, policy);
            } else {
                result = executeTask(stepDef, context, policy);
            }
            return result;
        } catch (Exception e) {
            var duration = Duration.between(start, Instant.now());
            return TaskResult.failed(stepDef.id(), stepDef.taskName(), duration, e.getMessage(), e);
        }
    }

    private TaskResult executeTask(StepDefinition stepDef, ExecutionContext context,
                                    com.performance.platform.domain.execution.RetryPolicy policy) {
        TaskExecutor executor = lookup.findTaskExecutor(stepDef.taskName());
        if (executor == null) {
            return TaskResult.failed(stepDef.id(), stepDef.taskName(),
                    Duration.ZERO, "No TaskExecutor found for taskName: " + stepDef.taskName(), null);
        }
        return retryExecutor.executeWithRetry(policy, () -> executor.execute(context, stepDef));
    }

    private TaskResult executeAssertion(StepDefinition stepDef, ExecutionContext context,
                                         com.performance.platform.domain.execution.RetryPolicy policy) {
        AssertionExecutor executor = lookup.findAssertionExecutor(stepDef.taskName());
        if (executor == null) {
            return TaskResult.failed(stepDef.id(), stepDef.taskName(),
                    Duration.ZERO, "No AssertionExecutor found for: " + stepDef.taskName(), null);
        }
        return retryExecutor.executeWithRetry(policy, () -> {
            var assertionResult = executor.evaluate(context, stepDef);
            return assertionResultToTaskResult(assertionResult, stepDef);
        });
    }

    static TaskResult assertionResultToTaskResult(
            com.performance.platform.domain.assertion.AssertionResult assertionResult,
            StepDefinition stepDef) {
        // ... code existant de DagPhaseExecutor.assertionResultToTaskResult()
    }
}
```

## LocalExecutionEngine — modification

```java
// Dans le constructeur de LocalExecutionEngine :
// AVANT :
this.dagPhaseExecutor = new DagPhaseExecutor(retryExecutor);
// APRES :
var dispatcher = new LocalStepDispatcher(taskExecutorLookup, retryExecutor);
this.dagPhaseExecutor = new DagPhaseExecutor(dispatcher);
```

`LocalExecutionEngine` ne change pas autrement. Il continue d'appeler `dagPhaseExecutor.executePhase()` avec les mêmes paramètres (moins `lookup` et `eventPublisher`).

## Règles Spécifiques

- `DagPhaseExecutor` dans `shared/` ne référence AUCUN type de `engine/local/` ou `engine/remote/`
- `StepDispatcher` est dans `shared/` — seule dépendance vers le domaine
- `LocalStepDispatcher` contient `assertionResultToTaskResult()` et `executeAssertion()` (code existant, juste déplacé)
- Le `DagPhaseExecutor` partagé ne publie PLUS les events (`TaskCompleted`, `TaskFailed`) — cette responsabilité remonte au caller (LocalExecutionEngine ou RemoteExecutionEngine). Les méthodes `publishTaskResult()` et `publishTaskSkipped()` sont supprimées du `DagPhaseExecutor` et restent dans les engines.
- La méthode `LOCAL_AGENT` constante est déplacée dans `LocalExecutionEngine` (pas dans shared)

## Tests

Déplacer `DagPhaseExecutorTest` dans `engine/shared/` et adapter :
```
platform-execution-engine/src/test/java/com/performance/platform/engine/shared/
  └── DagPhaseExecutorTest.java    — déplacé, test avec un StepDispatcher mock
```

Créer :
```
platform-execution-engine/src/test/java/com/performance/platform/engine/local/
  └── LocalStepDispatcherTest.java — NOUVEAU: test du dispatcher local
```

## Criteres de Done

- [ ] `DagPhaseExecutor` déplacé dans `engine/shared/` avec `StepDispatcher`
- [ ] `StepDispatcher` interface créée
- [ ] `LocalStepDispatcher` implémenté (appel direct executor + retry)
- [ ] `LocalExecutionEngine` utilise `DagPhaseExecutor` depuis shared
- [ ] `mvn test -pl platform-execution-engine -q` → 0 erreur
- [ ] Tous les tests `DagPhaseExecutorTest` passent dans leur nouveau package
- [ ] `LocalStepDispatcherTest` créé avec tests de dispatch
