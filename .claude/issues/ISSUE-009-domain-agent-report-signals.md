# ISSUE-009 — Events agent/assertion/report + AgentSignal scellé

**PDR** : PDR-002
**Module** : `platform-domain`
**Statut** : WAITING
**Priorité** : P0
**Bloquée par** : ISSUE-001, ISSUE-007
**Estime** : M

---

## Objectif

Créer les events agent/assertion/report et l'interface scellée `AgentSignal` avec
`ScenarioRestartSignal`.

## Fichiers à Créer

```
platform-domain/src/main/java/com/performance/platform/domain/event/
  ├── AssertionPassed.java
  ├── AssertionFailed.java
  ├── AgentRegistered.java
  ├── AgentLost.java
  ├── AgentRecovered.java
  ├── ReportGenerated.java
  ├── ReportPublished.java
  ├── AgentSignal.java            — sealed interface
  └── ScenarioRestartSignal.java  — implements AgentSignal

platform-domain/src/test/java/com/performance/platform/domain/event/
  └── SignalsTest.java — ScenarioRestartSignal instanceof AgentSignal, executionId nullable
```

## Interfaces à Implémenter

```java
public record AssertionPassed(ExecutionId executionId, TaskId assertionId, Instant timestamp) {}
public record AssertionFailed(ExecutionId executionId, TaskId assertionId, String reason, Instant timestamp) {}
public record AgentRegistered(AgentId agentId, AgentDescriptor descriptor, Instant timestamp) {}
public record AgentLost(AgentId agentId, String reason, Instant timestamp) {}
public record AgentRecovered(AgentId agentId, Instant timestamp) {}
public record ReportGenerated(ExecutionId executionId, ReportId reportId, Instant timestamp) {}
public record ReportPublished(ExecutionId executionId, ReportId reportId, PublicationTarget target, Instant timestamp) {}

public sealed interface AgentSignal permits ScenarioRestartSignal {
    SignalId id();
    Instant issuedAt();
}

public record ScenarioRestartSignal(SignalId id, ExecutionId executionId, String reason, Instant issuedAt)
        implements AgentSignal {}
```

## Règles Spécifiques

- `ScenarioRestartSignal.executionId` nullable = broadcast à tous les scénarios.
- `AgentSignal` scellé : seul `ScenarioRestartSignal` permis.

## Critères de Done

- [ ] `mvn test -pl platform-domain -q` → 0 erreur
- [ ] `new ScenarioRestartSignal(id, null, "X", now) instanceof AgentSignal` == true
- [ ] `.claude/progress.md` mis à jour : ISSUE-009 → DONE
- [ ] `.claude/context/interfaces-registry.md` : `AgentSignal`, `ScenarioRestartSignal`, events → STABLE
