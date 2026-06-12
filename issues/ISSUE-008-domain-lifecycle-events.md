# ISSUE-008 — Domain Events : cycle de vie scénario / phase / task

**PDR** : PDR-002
**Module** : `platform-domain`
**Statut** : WAITING
**Priorité** : P0
**Bloquée par** : ISSUE-001, ISSUE-002, ISSUE-004
**Estime** : M

---

## Objectif

Créer les records d'events du cycle de vie scénario, phase et task (verbe au passé, immuables).

## Fichiers à Créer

```
platform-domain/src/main/java/com/performance/platform/domain/event/
  ├── ScenarioStarted.java
  ├── ScenarioFinished.java
  ├── ScenarioCancelled.java
  ├── PhaseStarted.java
  ├── PhaseCompleted.java
  ├── TaskDispatched.java
  ├── TaskClaimedByAgent.java
  ├── TaskWorkInProgress.java
  ├── TaskStarted.java
  ├── TaskCompleted.java
  ├── TaskFailed.java
  └── TaskRetried.java

platform-domain/src/test/java/com/performance/platform/domain/event/
  └── LifecycleEventsTest.java — instanciation + égalité par valeur
```

## Interfaces à Implémenter

```java
public record ScenarioStarted(ExecutionId executionId, ScenarioId scenarioId, Instant timestamp) {}
public record ScenarioFinished(ExecutionId executionId, ScenarioId scenarioId, Verdict verdict, Duration duration, Instant timestamp) {}
public record ScenarioCancelled(ExecutionId executionId, ScenarioId scenarioId, String reason, Instant timestamp) {}
public record PhaseStarted(ExecutionId executionId, Phase phase, Instant timestamp) {}
public record PhaseCompleted(ExecutionId executionId, Phase phase, PhaseStatus status, Instant timestamp) {}
public record TaskDispatched(ExecutionId executionId, TaskId taskId, String taskName, MessageId messageId, Instant timestamp) {}
public record TaskClaimedByAgent(ExecutionId executionId, TaskId taskId, AgentId agentId, MessageId messageId, Instant timestamp) {}
public record TaskWorkInProgress(ExecutionId executionId, TaskId taskId, AgentId agentId, int progressPercent, String statusMessage, Instant timestamp) {}
public record TaskStarted(ExecutionId executionId, TaskId taskId, AgentId agentId, Instant timestamp) {}
public record TaskCompleted(ExecutionId executionId, TaskId taskId, AgentId agentId, TaskResult result, Duration duration, Instant timestamp) {}
public record TaskFailed(ExecutionId executionId, TaskId taskId, AgentId agentId, String error, int attempt, Instant timestamp) {}
public record TaskRetried(ExecutionId executionId, TaskId taskId, int attempt, Instant nextAttemptAt, Instant timestamp) {}
```

## Règles Spécifiques

- `TaskDispatched` utilise `String taskName` (pas `TaskType`).
- 0 annotation framework.

## Critères de Done

- [ ] `mvn test -pl platform-domain -q` → 0 erreur
- [ ] Tous les events instanciables, égalité par valeur
- [ ] `progress.md` mis à jour : ISSUE-008 → DONE
- [ ] `context/interfaces-registry.md` : events → STABLE
