# PDR-002 — Domain Events

**Module Maven** : `platform-domain`
**Package** : `com.performance.platform.domain.event`
**Statut** : WAITING
**Specs de référence** : `.claude/architecture.md` §4, `.claude/specifications/02-execution-engine.md` §8, `.claude/specifications/04-agent-runtime.md` §6-7, `.claude/specifications/05-transport-layer.md` §3
**Dépend de** : PDR-001
**Issues** : ISSUE-008, ISSUE-009

---

## Responsabilité

Définit tous les événements du domaine sous forme de records immuables (verbe au passé).
Ces events sont publiés via `ApplicationEventPublisher` (Spring) par les modules,
mais les records eux-mêmes restent 0-framework. Inclut aussi les signaux broadcast
(`ScenarioRestartSignal`) et l'interface scellée `AgentSignal`.

**Contrainte absolue** : 0 annotation Spring / JPA / Jackson.

---

## Interfaces Publiques

### Cycle de vie du scénario / phases

```java
public record ScenarioStarted(ExecutionId executionId, ScenarioId scenarioId, Instant timestamp) {}
public record ScenarioFinished(ExecutionId executionId, ScenarioId scenarioId, Verdict verdict, Duration duration, Instant timestamp) {}
public record ScenarioCancelled(ExecutionId executionId, ScenarioId scenarioId, String reason, Instant timestamp) {}
public record PhaseStarted(ExecutionId executionId, Phase phase, Instant timestamp) {}
public record PhaseCompleted(ExecutionId executionId, Phase phase, PhaseStatus status, Instant timestamp) {}
```

### Cycle de vie d'une task

```java
public record TaskDispatched(ExecutionId executionId, TaskId taskId, String taskName, MessageId messageId, Instant timestamp) {}
public record TaskClaimedByAgent(ExecutionId executionId, TaskId taskId, AgentId agentId, MessageId messageId, Instant timestamp) {}
public record TaskWorkInProgress(ExecutionId executionId, TaskId taskId, AgentId agentId, int progressPercent, String statusMessage, Instant timestamp) {}
public record TaskStarted(ExecutionId executionId, TaskId taskId, AgentId agentId, Instant timestamp) {}
public record TaskCompleted(ExecutionId executionId, TaskId taskId, AgentId agentId, TaskResult result, Duration duration, Instant timestamp) {}
public record TaskFailed(ExecutionId executionId, TaskId taskId, AgentId agentId, String error, int attempt, Instant timestamp) {}
public record TaskRetried(ExecutionId executionId, TaskId taskId, int attempt, Instant nextAttemptAt, Instant timestamp) {}
```

### Assertions / Agents / Reporting

```java
public record AssertionPassed(ExecutionId executionId, TaskId assertionId, Instant timestamp) {}
public record AssertionFailed(ExecutionId executionId, TaskId assertionId, String reason, Instant timestamp) {}

public record AgentRegistered(AgentId agentId, AgentDescriptor descriptor, Instant timestamp) {}
public record AgentLost(AgentId agentId, String reason, Instant timestamp) {}
public record AgentRecovered(AgentId agentId, Instant timestamp) {}

public record ReportGenerated(ExecutionId executionId, ReportId reportId, Instant timestamp) {}
public record ReportPublished(ExecutionId executionId, ReportId reportId, PublicationTarget target, Instant timestamp) {}
```

### Signaux broadcast

```java
public sealed interface AgentSignal permits ScenarioRestartSignal {
    SignalId id();
    Instant issuedAt();
}

public record ScenarioRestartSignal(
    SignalId id,
    ExecutionId executionId,          // nullable = tous les scénarios en cours
    String reason,                    // ex: "ORCHESTRATOR_RESTART"
    Instant issuedAt
) implements AgentSignal {}
```

---

## Règles de Comportement

- Tous les events sont des records immuables. Nom au passé (`TaskCompleted`, pas `CompleteTask`).
- `ScenarioRestartSignal.executionId` peut être null = broadcast à tous les scénarios.
- `AgentSignal` est une `sealed interface` ne permettant que `ScenarioRestartSignal` pour l'instant.
- Aucun de ces records ne contient de logique métier : ce sont des DTO d'événement.

---

## Dépendances Techniques

```
Ce PDR utilise :
  PDR-001 → ExecutionId, ScenarioId, TaskId, AgentId, MessageId, SignalId, ReportId,
            Phase, PhaseStatus, Verdict, TaskResult, AgentDescriptor, PublicationTarget

Ce PDR est utilisé par :
  PDR-006 (execution engine) → publie ScenarioStarted, TaskCompleted...
  PDR-007/008 (transport)    → transporte AgentSignal, events
  PDR-009 (agent runtime)    → publie TaskClaimedByAgent, TaskWorkInProgress
  PDR-015/016 (reporting)    → écoute ScenarioFinished
  PDR-017 (observability)    → écoute tous les events
```

---

## Critères de Done (PDR complet)

- [ ] Toutes les Issues du PDR sont DONE
- [ ] ArchUnit test : 0 import framework dans le package `event`
- [ ] Les 18 events + signal dans `.claude/context/interfaces-registry.md`
