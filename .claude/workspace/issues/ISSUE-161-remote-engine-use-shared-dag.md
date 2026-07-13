# ISSUE-161 — RemoteExecutionEngine uses Shared DagPhaseExecutor

**PDR** : PDR-039
**Module** : `platform-execution-engine`
**Statut** : APPROVED
**Priorite** : P1 (débloque la suppression de duplication)
**Bloquee par** : ISSUE-160 (DagPhaseExecutor + StepDispatcher dans shared/)
**Estime** : M (2-3h)

---

## Objectif

Refactorer `RemoteExecutionEngine` pour utiliser `DagPhaseExecutor` (du package `shared/`) avec un `RemoteStepDispatcher` qui encapsule le dispatch via `ExecutionTransport`. Supprimer la logique DAG dupliquée (~120 lignes).

## Fichiers à Créer

```
platform-execution-engine/src/main/java/com/performance/platform/engine/remote/
  └── RemoteStepDispatcher.java    — NOUVEAU: implémentation distribuée du StepDispatcher
```

## Fichiers à Modifier

```
platform-execution-engine/src/main/java/com/performance/platform/engine/remote/
  └── RemoteExecutionEngine.java   — utiliser DagPhaseExecutor + RemoteStepDispatcher
```

## Structure

### RemoteStepDispatcher

```java
package com.performance.platform.engine.remote;

import com.performance.platform.application.config.ExecutionConfig;
import com.performance.platform.domain.execution.ExecutionContext;
import com.performance.platform.domain.execution.ExecutionStep;
import com.performance.platform.domain.execution.PartialExecutionContext;
import com.performance.platform.domain.execution.RetryPolicy;
import com.performance.platform.domain.execution.TaskCompletionPolicy;
import com.performance.platform.domain.id.MessageId;
import com.performance.platform.domain.id.TaskId;
import com.performance.platform.domain.scenario.Phase;
import com.performance.platform.domain.scenario.StepDefinition;
import com.performance.platform.domain.task.TaskResult;
import com.performance.platform.domain.task.TaskStatus;
import com.performance.platform.engine.availability.AgentAvailabilityChecker;
import com.performance.platform.engine.correlation.TaskCorrelationTracker;
import com.performance.platform.engine.shared.StepDispatcher;
import com.performance.platform.transport.ExecutionTransport;
import com.performance.platform.transport.message.TaskExecutionRequest;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Implémentation distribuée du {@link StepDispatcher}.
 * Diffuse la tâche aux agents via {@link ExecutionTransport} et attend
 * les résultats selon la {@link TaskCompletionPolicy}.
 */
public class RemoteStepDispatcher implements StepDispatcher {

    private final ExecutionTransport transport;
    private final AgentAvailabilityChecker availabilityChecker;
    private final TaskCorrelationTracker tracker;
    private final ExecutionConfig config;

    public RemoteStepDispatcher(
            ExecutionTransport transport,
            AgentAvailabilityChecker availabilityChecker,
            TaskCorrelationTracker tracker,
            ExecutionConfig config) {
        this.transport = transport;
        this.availabilityChecker = availabilityChecker;
        this.tracker = tracker;
        this.config = config;
    }

    @Override
    public TaskResult dispatch(ExecutionStep execStep, ExecutionContext context, Phase phase) {
        StepDefinition stepDef = execStep.step();

        // 1. Await agent
        availabilityChecker.awaitAgentFor(stepDef.taskName(), config.taskAvailabilityTimeout());

        // 2. Build partial context
        var partialCtx = PartialContextBuilder.build(context, execStep.requiredContextKeys());

        // 3. Create request (broadcast)
        var messageId = MessageId.generate();
        RetryPolicy retry = stepDef.retryPolicy() != null ? stepDef.retryPolicy() : RetryPolicy.defaults();
        var request = new TaskExecutionRequest(
                messageId, /* executionId from context */ null, stepDef, partialCtx, Instant.now(), retry);

        // 4. Dispatch
        transport.dispatchTask(request);

        // 5. Track
        tracker.trackDispatched(messageId, stepDef.id(), /* executionId */ null);

        // 6. Await completion (avec timeout)
        return awaitCompletion(messageId, stepDef.id(), config);
    }

    private TaskResult awaitCompletion(MessageId messageId, TaskId taskId, ExecutionConfig config) {
        long deadlineMs = System.currentTimeMillis() + config.taskExecutionTimeout().toMillis();
        TaskCompletionPolicy policy = config.completionPolicy();

        while (System.currentTimeMillis() < deadlineMs) {
            if (tracker.isComplete(messageId, policy)) {
                Map<String, TaskResult> results = tracker.getResults(messageId);
                // Retourner le premier résultat (ou agréger selon la policy)
                if (results != null && !results.isEmpty()) {
                    return results.values().iterator().next();
                }
                return TaskResult.failed(taskId, taskId.value(),
                        Duration.ZERO, "No results received", null);
            }
            try {
                Thread.sleep(RemoteExecutionEngine.POLL_COMPLETION_MS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return TaskResult.failed(taskId, taskId.value(),
                        Duration.ZERO, "Interrupted while awaiting completion", null);
            }
        }

        return TaskResult.failed(taskId, taskId.value(),
                config.taskExecutionTimeout(), "Task execution timed out", null);
    }
}
```

### RemoteExecutionEngine — refactoring

Le `RemoteExecutionEngine` après refactoring :

1. **Garde** : `execute()`, `initializeExecution()`, `finalizeExecution()`, `getStatus()`, `cancel()`, `applyVerdict()`, `computeVerdict()`, event handler (`onTransportEvent`), `handleTaskClaimed/Completed/Failed/Wip`, `reconstructTaskResult()`, `ActiveExecution`, `PendingDispatch`
2. **Supprime** : `groupStepsByLevel()`, `classifySteps()`, `LevelClassification`, `allDependenciesSatisfied()`, `checkFailedInPhase()`, `checkSkippedInAnyPhase()`, `checkSkippedInSteps()`, `dispatchAndWaitLevel()`, `LevelResult`, `awaitCompletion()` (délégué au dispatcher)
3. **Modifie** : `executePhase()` utilise `dagPhaseExecutor.executePhase()` au lieu de sa propre boucle

```java
// Dans RemoteExecutionEngine.executePhase() — APRES refactoring :
private ExecutionContext executePhase(
        Phase phase, List<ExecutionStep> steps,
        ExecutionContext context, ExecutionId executionId,
        AtomicBoolean cancelled) {

    if (steps == null || steps.isEmpty()) {
        return context;
    }

    eventPublisher.publishEvent(new PhaseStarted(executionId, phase, Instant.now()));
    executionRepository.updatePhase(executionId, phase, PhaseStatus.RUNNING);

    DagPhaseExecutor.PhaseResult result = dagPhaseExecutor.executePhase(
            steps, context, phase, remoteDispatcher, cancelled);

    boolean anyFailed = checkFailedInPhase(result.updatedContext(), steps);
    PhaseStatus phaseStatus = anyFailed ? PhaseStatus.FAILED : PhaseStatus.COMPLETED;

    eventPublisher.publishEvent(new PhaseCompleted(executionId, phase, phaseStatus, Instant.now()));
    executionRepository.updatePhase(executionId, phase, phaseStatus);

    ActiveExecution exec = activeExecutions.get(executionId.value());
    if (exec != null) {
        ExecutionState updated = updatePhaseInState(exec.state, phase, phaseStatus, result.updatedContext());
        exec.state = updated;
        executionRepository.save(updated);
    }

    return result.updatedContext();
}
```

Les méthodes `checkFailedInPhase()`, `checkSkippedInAnyPhase()`, `checkSkippedInSteps()` sont **conservées** dans `RemoteExecutionEngine` car elles diffèrent légèrement de la version locale (elles itèrent sur `agentResults.values()` au lieu de chercher `LOCAL_AGENT`).

## Règles Spécifiques

- `RemoteStepDispatcher` encapsule TOUTE la logique de dispatch distribuée (availability check, partial context, transport, tracking, await completion)
- `DagPhaseExecutor` partagé ne connaît RIEN du transport ou des agents
- Les `TaskDispatched` et `TaskClaimedByAgent` events continuent d'être publiés par le `RemoteExecutionEngine` (pas par le dispatcher)
- Le `RemoteStepDispatcher.awaitCompletion()` est une version simplifiée — le multi-agent et le `PendingDispatch` restent gérés par le `RemoteExecutionEngine` via le handler d'events (qui peuple le tracker)

## Criteres de Done

- [ ] `RemoteStepDispatcher` implémenté
- [ ] `RemoteExecutionEngine.executePhase()` utilise `DagPhaseExecutor` + `RemoteStepDispatcher`
- [ ] Logique DAG dupliquée supprimée de `RemoteExecutionEngine` (ISSUE-162, peut être fait ensemble)
- [ ] `mvn test -pl platform-execution-engine -q` → 0 erreur
- [ ] Test d'intégration RemoteExecutionEngine (existant) passe sans modification
