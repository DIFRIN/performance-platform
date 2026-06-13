# ISSUE-024 — RemoteExecutionEngine (dispatch + multi-claim)

**PDR** : PDR-006
**Module** : `platform-execution-engine`
**Statut** : WAITING
**Priorité** : P1
**Bloquée par** : ISSUE-021, ISSUE-022, ISSUE-023, ISSUE-026
**Estime** : L

---

## Objectif

Implémenter `RemoteExecutionEngine` : distribue les tasks via `ExecutionTransport` en
broadcast, suit les claims/résultats multi-agents, applique la `TaskCompletionPolicy`.

## Fichiers à Créer

```
platform-execution-engine/src/main/java/com/performance/platform/engine/remote/
  ├── RemoteExecutionEngine.java       — implements ExecutionEngine
  └── PartialContextBuilder.java       — extrait PartialExecutionContext depuis requiredContextKeys

platform-execution-engine/src/test/java/com/performance/platform/engine/remote/
  ├── RemoteExecutionEngineTest.java   — dispatch, claim, completion policies
  └── PartialContextBuilderTest.java
```

## Interfaces à Implémenter

```java
@Service
@ConditionalOnProperty(name = "runtime.mode", havingValue = "DISTRIBUTED")
public class RemoteExecutionEngine implements ExecutionEngine {
    // deps : ExecutionPlanBuilder, AgentAvailabilityChecker, TaskCorrelationTracker,
    //        ExecutionTransport, ExecutionRepository, ExecutionConfig, ApplicationEventPublisher
}
```

## Règles Spécifiques

- Pour chaque step (ordre DAG) :
  1. `awaitAgentFor(step.taskName, config.taskAvailabilityTimeout())`
  2. construire `PartialExecutionContext` depuis `requiredContextKeys`
  3. créer `TaskExecutionRequest` (sans targetAgentId — broadcast)
  4. `transport.dispatchTask(request)` + `tracker.trackDispatched(...)` + publier `TaskDispatched`
- Sur events reçus (`subscribe`) :
  - `TaskClaimedByAgent` → `tracker.onClaimed()`
  - `TaskWorkInProgress` → reset timer (NOTE R6 : interval ≤ taskExecutionTimeout/3)
  - `TaskCompleted` → stocker `context[taskId][agentId]`, `repository.saveTaskResult()`
  - `TaskFailed` → retry si configuré, sinon marquer failed
- Complétion selon `completionPolicy` (FIRST_COMPLETE | ALL_COMPLETE) avant le step suivant.
- Agent perdu (TTL) → `transport.broadcastSignal(ScenarioRestartSignal)` → scénario FAILED.
- Aucun agent après timeout → `NoAvailableAgentException` → scénario FAILED.
- I/O sous Virtual Threads.

## Critères de Done

- [ ] `mvn test -pl platform-execution-engine -q` → 0 erreur
- [ ] Dispatch broadcast sans targetAgentId (vérifié)
- [ ] ALL_COMPLETE attend N résultats ; FIRST_COMPLETE avance au 1er
- [ ] `.claude/progress.md` mis à jour : ISSUE-024 → DONE
- [ ] `.claude/context/interfaces-registry.md` : `RemoteExecutionEngine` → STABLE
