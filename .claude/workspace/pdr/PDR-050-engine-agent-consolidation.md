# PDR-050 — Engine & Agent Consolidation (collapse the mode fork)

**Module Maven** : `platform-execution-engine`, `platform-agent-runtime`, `platform-bootstrap`
**Package** : `com.performance.platform.engine`, `com.performance.platform.agent.runtime`
**Statut** : WAITING
**Specs de référence** : `improvements.md` A1, §3 (target architecture) ; `ADR-026-assembly-strategy-thin-composition-root.md`
**Dépend de** : PDR-043 (ISSUE-170 équivalence), PDR-048 (ISSUE-180 boundary test)
**Issues** : ISSUE-184, ISSUE-185, ISSUE-186, ISSUE-187, ISSUE-188

---

## Responsabilité

Supprimer la duplication LOCAL/DISTRIBUTED (~1 900 LOC) en fusionnant :
- `LocalExecutionEngine` + `RemoteExecutionEngine` → UN `ExecutionEngine`
  paramétré par `StepDispatcher` (déjà extrait, PDR-039).
- `LocalAgent` + `DistributedAgentRuntime` → UN `TaskAgentRuntime` paramétré
  par `ExecutionTransport` + un `AgentRegistrationPort` optionnel.

Le mode devient un choix de transport + un choix de localisation d'exécution,
exactement ce que `ExecutionTransport` était censé permettre. Gate : le test
d'équivalence (T2) et le test de frontière (T1) doivent être verts avant/pendant.

> ADR requise (ADR-037) : suppression des 4 classes publiques et remplacement
> par un engine unique + un runtime d'agent unique.

---

## Interfaces Publiques

```java
// platform-execution-engine
package com.performance.platform.engine;

@Service
@ConditionalOnExpression(
    "'${runtime.mode:LOCAL}'.equals('LOCAL') || ('${runtime.mode:LOCAL}'.equals('DISTRIBUTED')"
    + " && '${runtime.role:NONE}'.equals('ORCHESTRATOR'))")
public class ExecutionEngine implements ExecuteScenarioUseCase, CancelExecutionUseCase {

    public ExecutionEngine(
            ExecutionPlanBuilder planBuilder,
            ExecutionRepository executionRepository,
            ApplicationEventPublisher eventPublisher,
            ExecutionLifecycleDispatcher lifecycleDispatcher,
            StepDispatcher stepDispatcher);   // LocalStepDispatcher (LOCAL) | RemoteStepDispatcher (DISTRIBUTED)

    @Override public ExecutionId execute(ScenarioDefinition scenario);
    @Override public void cancel(ExecutionId id);
    public ExecutionStatus getStatus(ExecutionId id);
    // Helpers partagés (déjà dupliqués) : initializeExecution, computeVerdict, applyVerdict,
    // finalizeExecution, checkFailedInPhase, checkSkippedInAnyPhase, checkSkippedInSteps,
    // dispatchLifecycleSignal, createInitialState, updateState, updatePhaseInState
}
```

La souscription transport du `RemoteExecutionEngine` (`onTransportEvent`) sort du
moteur et devient un bean DISTRIBUTED séparé `TransportExecutionEventHandler`
(implémente `ExecutionEventHandler`) qui alimente tracker + repository + events.

```java
// platform-agent-runtime
package com.performance.platform.agent.runtime;

public class TaskAgentRuntime implements AgentRuntime {

    public TaskAgentRuntime(
            ExecutionTransport transport,
            TaskSpecializationFilter filter,
            AgentDescriptor descriptor,
            Duration taskExecutionTimeout,
            List<TaskExecutor> taskExecutors,
            List<StatefulResourceCleaner> cleaners,
            AgentRegistrationPort registrationPort,   // null en LOCAL
            Duration heartbeatInterval);              // ignoré si registrationPort == null

    @Override public void start();   // connect ; register+heartbeat si registrationPort != null ; receiveTask/receiveSignal
    @Override public void stop();    // drain ; deregister si registrationPort != null ; disconnect
    @Override public AgentState getState();
    @Override public AgentDescriptor getDescriptor();
    @Override public boolean canExecute(String taskName);
    @Override public void onScenarioRestart(ScenarioRestartSignal signal);
    @Override public void onLifecycleSignal(ExecutionLifecycleSignal signal);
}
```

Les classes supprimées : `LocalExecutionEngine`, `RemoteExecutionEngine`,
`LocalAgent`, `DistributedAgentRuntime`. `LocalLifecycleDispatcher`/`RemoteLifecycleDispatcher`
restent les implémentations de `ExecutionLifecycleDispatcher` (câblées dans platform-bootstrap).

---

## Règles de Comportement

- Aucun `if/switch` sur `runtime.mode` dans le code métier : la sélection du
  `StepDispatcher` et du `AgentRegistrationPort` (null vs réel) est faite par
  des `@Bean` conditionnels Spring (CF-03).
- Le comportement observable LOCAL et DISTRIBUTED reste identique : le test
  d'équivalence (ISSUE-170) doit rester vert après fusion.
- `TaskAgentRuntime` en LOCAL : `registrationPort == null`, `heartbeatInterval` ignoré,
  pas de REGISTERING (cycle OFFLINE→IDLE→EXECUTING→DRAINING→OFFLINE).

---

## Dépendances Techniques

```
Ce PDR utilise :
  PDR-039 (StepDispatcher/DagPhaseExecutor), PDR-045 (lifecycle ingestion),
  PDR-009 (AgentRuntime/TaskExecutionPipeline), PDR-037 (lifecycle dispatchers)

Ce PDR est utilisé par :
  PDR-051 (completion semantics — RemoteStepDispatcher reste le seul dispatch distant)
```

---

## Critères de Done (PDR complet)

- [ ] `ExecutionEngine` unique + `TransportExecutionEventHandler` (ISSUE-184)
- [ ] `LocalExecutionEngine`/`RemoteExecutionEngine` supprimés + config réécrite (ISSUE-185)
- [ ] `TaskAgentRuntime` unique (ISSUE-186)
- [ ] `LocalAgent`/`DistributedAgentRuntime` supprimés + config réécrite (ISSUE-187)
- [ ] `LocalDistributedEquivalenceTest` + `ModuleDependencyArchTest` verts post-fusion (ISSUE-188)
