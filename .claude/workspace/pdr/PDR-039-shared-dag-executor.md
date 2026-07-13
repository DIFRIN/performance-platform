# PDR-039 — Shared DAG Executor Extraction

**Module Maven** : `platform-execution-engine`
**Package** : `com.performance.platform.engine.shared`
**Status** : WAITING
**Specs de reference** : `.claude/knowledge/specs/02-execution-engine.md`
**Depend de** : PDR-006 (ExecutionPlanBuilder, DagPhaseExecutor — déjà DONE)
**Issues** : ISSUE-160, ISSUE-161, ISSUE-162

---

## Responsabilite

Extraire `DagPhaseExecutor` du package `engine/local/` vers un package partagé `engine/shared/`, afin que les deux engines (Local et Remote) utilisent la même implémentation de parcours DAG. Actuellement, `RemoteExecutionEngine` duplique ~120 lignes de logique DAG (groupByLevel, classifySteps, allDependenciesSatisfied, checkFailedInPhase, checkSkippedInAnyPhase) qui existent déjà dans `DagPhaseExecutor`.

La différence entre les deux engines est UNIQUEMENT la stratégie de dispatch des steps :
- **LOCAL** : appel direct `executor.execute(context, step)` dans un Virtual Thread
- **DISTRIBUTED** : `transport.dispatchTask(request)` + attente des completions

Le parcours DAG (regroupement par niveau, classification runnable/skippable, vérification des dépendances) est identique.

---

## Interfaces Publiques

### DagPhaseExecutor (déplacé, pas modifié)

```java
// Déplacé de engine/local/ vers engine/shared/
package com.performance.platform.engine.shared;

public class DagPhaseExecutor {
    // ... identique à l'existant, pas de changement d'interface publique

    public PhaseResult executePhase(
            List<ExecutionStep> steps,
            ExecutionContext context,
            Phase phase,
            StepDispatcher dispatcher,        // NOUVEAU: abstraction du dispatch
            AtomicBoolean cancelled) { ... }
}
```

### StepDispatcher (nouvelle interface fonctionnelle)

```java
package com.performance.platform.engine.shared;

/**
 * Abstrait la stratégie d'exécution d'un step.
 * En LOCAL : appel direct à l'executor.
 * En DISTRIBUTED : dispatch via transport + attente completion.
 */
@FunctionalInterface
public interface StepDispatcher {
    /**
     * Exécute un step et retourne le TaskResult.
     * L'implémentation est responsable du retry (la RetryPolicy est dans stepDef).
     */
    TaskResult dispatch(ExecutionStep execStep, ExecutionContext context, Phase phase);
}
```

Deux implémentations :
- `LocalStepDispatcher` : utilise `TaskExecutorLookup` + `RetryExecutor` pour appeler l'executor directement
- `RemoteStepDispatcher` : utilise `ExecutionTransport.dispatchTask()` + `TaskCorrelationTracker` pour le dispatch distribué

---

## Design

### Avant (duplication)

```
LocalExecutionEngine          RemoteExecutionEngine
  ├── DagPhaseExecutor           ├── groupStepsByLevel()      ★ DUPLIQUÉ
  │   ├── groupStepsByLevel()    ├── classifySteps()          ★ DUPLIQUÉ
  │   ├── classifySteps()        ├── allDependenciesSatisfied() ★ DUPLIQUÉ
  │   ├── executeLevel()         ├── checkFailedInPhase()     ★ DUPLIQUÉ
  │   │   ├── VThreads           ├── checkSkippedInAnyPhase() ★ DUPLIQUÉ
  │   │   └── executeSingleStep  ├── dispatchAndWaitLevel()
  │   └── allDependenciesSat.    │   ├── availabilityChecker
  └── ...                        │   ├── transport.dispatchTask()
                                 │   └── tracker + awaitCompletion
                                 └── ...
```

### Après (DAG partagé)

```
engine/shared/
  └── DagPhaseExecutor          ← logique DAG UNIQUE
      ├── groupStepsByLevel()
      ├── classifySteps()
      ├── allDependenciesSatisfied()
      ├── executeLevel()        ← utilise StepDispatcher
      └── PhaseResult

engine/local/
  └── LocalStepDispatcher       ← appel direct executor
      └── TaskExecutorLookup + RetryExecutor

engine/remote/
  └── RemoteStepDispatcher      ← transport.dispatchTask() + tracker
      └── ExecutionTransport + TaskCorrelationTracker
```

---

## Flux

```
DagPhaseExecutor.executePhase(steps, context, phase, dispatcher, cancelled)
  │
  ├── groupStepsByLevel(steps)
  │
  └── for each level (sorted):
      ├── classifySteps(levelSteps, context)
      │   ├── runnable:  allDependenciesSatisfied == true
      │   └── skippable: au moins une dépendance FAILED/SKIPPED
      │
      ├── Mark skippable → context.with(taskId, SKIPPED)
      │
      └── executeLevel(runnable, context, phase, dispatcher)
          └── for each step (parallel VThreads):
              dispatcher.dispatch(step, context, phase) → TaskResult
```

Le `StepDispatcher` fait TOUT le reste (retry, lookup executor, transport, attente).

---

## Dependances Techniques

```
Ce PDR utilise :
  PDR-006 (ExecutionPlanBuilder, DagPhaseExecutor) → déjà DONE

Ce PDR est utilisé par :
  (aucun — PDR autonome)
```

---

## Criteres de Done (PDR complet)

- [ ] `DagPhaseExecutor` déplacé dans `engine/shared/` (ISSUE-160)
- [ ] `StepDispatcher` interface créée dans `engine/shared/` (ISSUE-160)
- [ ] `LocalStepDispatcher` implémenté dans `engine/local/` (ISSUE-160)
- [ ] `LocalExecutionEngine` utilise `DagPhaseExecutor` + `LocalStepDispatcher` (ISSUE-160)
- [ ] `RemoteExecutionEngine` refactoré pour utiliser `DagPhaseExecutor` (ISSUE-161)
- [ ] Logique DAG dupliquée supprimée de `RemoteExecutionEngine` (ISSUE-162)
- [ ] `mvn test -pl platform-execution-engine -q` → 0 erreur
- [ ] Tous les tests existants passent sans modification (refactor pur)
