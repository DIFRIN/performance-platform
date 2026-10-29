# ADR-037 — Consolidation : un moteur et un runtime d'agent uniques

**Date** : 2026-10-20
**Statut** : ACCEPTED
**Décideurs** : Architect + implémentation (Claude Code), sur constat de l'audit `improvements.md` (A1)
**Raffine** : ADR-026 (composition root), PDR-039 (DagPhaseExecutor), PDR-045 (lifecycle)
**Impacte interfaces publiques** : OUI — suppression de `LocalExecutionEngine`, `RemoteExecutionEngine`, `LocalAgent`, `DistributedAgentRuntime`

---

## Contexte

LOCAL et DISTRIBUTED étaient deux implémentations parallèles du même moteur
(~1 900 LOC dupliquées) et du même agent. Le port `ExecutionTransport` existait
déjà, mais le fork persistait sur `runtime.mode`. T2 (`LocalDistributedEquivalenceIT`)
et T1 (`ModuleDependencyTest`) étaient verts avant la fusion.

## Décision

### Moteur

- Un unique `ExecutionEngine` (LOCAL et DISTRIBUTED + ORCHESTRATOR), paramétré par
  un `StepDispatcher` : `LocalStepDispatcher` ou `RemoteStepDispatcher`.
- Les helpers (`initializeExecution`, `computeVerdict`, `applyVerdict`,
  `finalizeExecution`, `checkFailedInPhase`, `checkSkipped*`,
  `dispatchLifecycleSignal`, machine d'état) n'existent plus qu'une fois.
- `TransportExecutionEventHandler` consomme les événements du transport en
  DISTRIBUTED (tracker + repository + événements) ; l'engine ne connaît plus le
  transport.
- `OutcomeEventPublisher` : publication locale des `TaskCompleted/TaskFailed`
  en LOCAL ; no-op en DISTRIBUTED (le handler les publie avec l'agent réel).
- Suppression de `LocalExecutionEngine` et `RemoteExecutionEngine`.

### Agent

- Un unique `TaskAgentRuntime`, paramétré par `ExecutionTransport`,
  `TaskSpecializationFilter` et un `AgentRegistrationPort` **optionnel** :
  - `registrationPort == null` → LOCAL : pas de REGISTERING, pas de heartbeat,
    pas de deregister ;
  - `registrationPort != null` → DISTRIBUTED : register + heartbeat + deregister.
- Tous les champs concurrents et méthodes de cycle de vie sont uniques.
- Suppression de `LocalAgent` et `DistributedAgentRuntime`.

## Conséquences

- ~1 900 LOC dupliquées remplacées par un moteur + un runtime.
- La sélection LOCAL/DISTRIBUTED est purement déclarative (`@Bean` conditionnels).
- `LocalDistributedEquivalenceIT` reste vert (même verdict, mêmes statuts).
- Les tests historiques `LocalAgentTest`/`DistributedAgentRuntimeTest` couvrent
  les deux configurations du runtime unique.

## Preuves

- `LocalDistributedEquivalenceIT` vert.
- `ModuleDependencyTest` vert (6 règles).
- `LocalFlowIT`, `LocalModeAllTasksIT`, `WebUiApiIT` verts.
- `mvn test -pl :platform-execution-engine,:platform-agent-runtime,:platform-bootstrap` vert.
