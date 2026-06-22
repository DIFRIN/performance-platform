# PDR-006 — Execution Engine

**Module Maven** : `platform-execution-engine`
**Package** : `com.performance.platform.engine`
**Statut** : WAITING
**Specs de référence** : `.claude/knowledge/specs/02-execution-engine.md` (complet), ADR-011
**Dépend de** : PDR-001, PDR-002, PDR-004, PDR-005, PDR-007
**Issues** : ISSUE-019, ISSUE-020, ISSUE-021, ISSUE-022, ISSUE-023, ISSUE-024

---

## Responsabilité

Transforme un `ScenarioDefinition` en `ExecutionPlan` (DAG par phase), puis orchestre
l'exécution séquentielle des phases `PREPARATION → INJECTION → ASSERTION`. Deux
implémentations : `LocalExecutionEngine` (in-process, Virtual Threads) et
`RemoteExecutionEngine` (dispatch via transport + multi-claim). Gère retry, timeout,
checkpointing, et publication d'events.

---

## Interfaces Publiques

```java
public interface ExecutionEngine {
    ExecutionId execute(ScenarioDefinition scenario) throws ExecutionException;
    ExecutionStatus getStatus(ExecutionId id);
    void cancel(ExecutionId id);
}

public interface ExecutionPlanBuilder {
    ExecutionPlan build(ScenarioDefinition scenario);
}

public interface RetryExecutor {
    <T> T executeWithRetry(RetryPolicy policy, Supplier<T> action);
}

public interface TaskCorrelationTracker {
    void trackDispatched(MessageId messageId, TaskId taskId, ExecutionId executionId);
    void onClaimed(MessageId messageId, AgentId agentId);
    void onCompleted(MessageId messageId, AgentId agentId, TaskResult result);
    void onFailed(MessageId messageId, AgentId agentId, String error);
    Set<AgentId> claimsFor(MessageId messageId);
    boolean isComplete(MessageId messageId, TaskCompletionPolicy policy);
}

public interface AgentAvailabilityChecker {
    /**
     * Attend jusqu'à taskAvailabilityTimeout qu'un agent compétent existe.
     * @throws NoAvailableAgentException si aucun agent après le timeout.
     */
    void awaitAgentFor(String taskName, Duration taskAvailabilityTimeout);
    boolean hasAgentFor(String taskName);
}
```

### Implémentations (signatures Spring)

```java
@Service
@ConditionalOnProperty(name = "runtime.mode", havingValue = "LOCAL")
public class LocalExecutionEngine implements ExecutionEngine { /* ... */ }

@Service
@ConditionalOnProperty(name = "runtime.mode", havingValue = "DISTRIBUTED")
public class RemoteExecutionEngine implements ExecutionEngine { /* ... */ }

@Component
public class DefaultExecutionPlanBuilder implements ExecutionPlanBuilder { /* ... */ }
```

---

## Règles de Comportement

- Séquencement OBLIGATOIRE : PREPARATION termine entièrement → INJECTION → ASSERTION (toujours, même si INJECTION failed).
- DAG par phase : grouper par `dagLevel`, exécuter chaque niveau en parallèle via `Executors.newVirtualThreadPerTaskExecutor()`.
- Si un prérequis a FAILED → step SKIPPED (sauf dépendance optionnelle).
- Cycle dans DAG → `InvalidScenarioException` au `build()` (avant exécution).
- Retry : backoff exponentiel (`initialDelay × multiplier^n`, plafonné à `maxDelay`).
- `LocalExecutionEngine` : exécute via `TaskExecutorRegistry` local (PDR-010). completionPolicy = FIRST_COMPLETE.
- `RemoteExecutionEngine` :
  1. build plan ; 2. `awaitAgentFor` chaque taskName ; 3. construire `PartialExecutionContext` depuis `requiredContextKeys` ; 4. créer `TaskExecutionRequest` (sans targetAgentId) ; 5. `transport.dispatchTask()` ; 6. `trackDispatched()`.
- Collecte des events : `TaskClaimedByAgent` → `onClaimed()` ; `TaskWorkInProgress` → reset timer ; `TaskCompleted` → stocke `context["taskId"]["agentId"]` ; `TaskFailed` → retry si configuré.
- Complétion selon `completionPolicy` : FIRST_COMPLETE (1 résultat) ou ALL_COMPLETE (tous les claims).
- Agent perdu (TTL) → `ScenarioRestartSignal` broadcast → scénario FAILED.
- Persistance : checkpoint `ExecutionState` après chaque phase via `ExecutionRepository`.
- Events publiés via `ApplicationEventPublisher` : `ScenarioStarted`, `PhaseStarted/Completed`, `TaskDispatched`, `TaskCompleted/Failed`, `ScenarioFinished`.

---

## Dépendances Techniques

```
Ce PDR utilise :
  PDR-001 → ExecutionPlan, ExecutionStep, ExecutionContext, PartialExecutionContext,
            TaskResult, RetryPolicy, TaskCompletionPolicy
  PDR-002 → tous les events + ScenarioRestartSignal
  PDR-004 → ExecuteScenarioUseCase, ExecutionRepository, AgentRegistryPort, ExecutionConfig
  PDR-005 → ScenarioDefinition (entrée)
  PDR-007 → ExecutionTransport, TaskExecutionRequest

Ce PDR est utilisé par :
  PDR-018 (platform-app) → orchestration finale
```

---

## Critères de Done (PDR complet)

- [ ] Toutes les Issues du PDR sont DONE
- [ ] Tests : DAG ordering, détection cycle, retry backoff, multi-claim ALL_COMPLETE
- [ ] Interfaces dans `.claude/context/interfaces-registry.md` STABLE
