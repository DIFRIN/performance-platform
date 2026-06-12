# ISSUE-023 — LocalExecutionEngine (Virtual Threads, in-process)

**PDR** : PDR-006
**Module** : `platform-execution-engine`
**Statut** : WAITING
**Priorité** : P1
**Bloquée par** : ISSUE-019, ISSUE-020, ISSUE-013
**Estime** : L

---

## Objectif

Implémenter `LocalExecutionEngine` : exécute les 3 phases en séquence avec DAG parallèle
(Virtual Threads), checkpoint après chaque phase, publication d'events.

## Fichiers à Créer

```
platform-execution-engine/src/main/java/com/performance/platform/engine/local/
  ├── LocalExecutionEngine.java   — implements ExecutionEngine
  └── DagPhaseExecutor.java       — exécute une phase niveau par niveau

platform-execution-engine/src/main/java/com/performance/platform/engine/
  └── ExecutionEngine.java        — interface (port)

platform-execution-engine/src/test/java/com/performance/platform/engine/local/
  └── LocalExecutionEngineTest.java — séquence des phases, parallélisme, SKIPPED sur dep failed
```

## Interfaces à Implémenter

```java
public interface ExecutionEngine {
    ExecutionId execute(ScenarioDefinition scenario) throws ExecutionException;
    ExecutionStatus getStatus(ExecutionId id);
    void cancel(ExecutionId id);
}

@Service
@ConditionalOnProperty(name = "runtime.mode", havingValue = "LOCAL")
public class LocalExecutionEngine implements ExecutionEngine {
    // deps : ExecutionPlanBuilder, RetryExecutor, ExecutionRepository,
    //        ApplicationEventPublisher, TaskExecutorRegistry (PDR-010, injecté)
}
```

## Règles Spécifiques

- Séquence OBLIGATOIRE : PREPARATION (entièrement) → INJECTION → ASSERTION (toujours).
- Chaque phase : grouper par `dagLevel`, exécuter en parallèle via `Executors.newVirtualThreadPerTaskExecutor()`.
- Prérequis FAILED → step `TaskResult.skipped(...)`.
- completionPolicy LOCAL = FIRST_COMPLETE (1 seul agent local).
- Stocker chaque résultat dans `ExecutionContext.with(stepId, Map.of("agent-local", taskResult))`.
- Checkpoint via `ExecutionRepository.save()` après chaque phase ; `updatePhase()` aux transitions.
- Events : `ScenarioStarted`, `PhaseStarted/Completed`, `TaskCompleted/Failed`, `ScenarioFinished` (avec Verdict).
- `TaskExecutorRegistry` est injecté depuis PDR-010 (dépendance Maven vers platform-infrastructure côté assembly ; ici on dépend de son interface via une abstraction locale ou via platform-application — utiliser l'interface `TaskExecutorRegistry` exposée). NOTE : si l'interface n'est pas encore dispo au build, mocker dans les tests.

## Critères de Done

- [ ] `mvn test -pl platform-execution-engine -q` → 0 erreur
- [ ] Les 3 phases s'exécutent dans l'ordre ; ASSERTION même si INJECTION failed
- [ ] Steps indépendants exécutés en parallèle (Virtual Threads)
- [ ] `progress.md` mis à jour : ISSUE-023 → DONE
- [ ] `context/interfaces-registry.md` : `ExecutionEngine`, `LocalExecutionEngine` → STABLE
