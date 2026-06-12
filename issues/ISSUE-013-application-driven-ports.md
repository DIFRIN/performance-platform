# ISSUE-013 — Ports sortants (Repository / AgentRegistry / ReportPublisher)

**PDR** : PDR-004
**Module** : `platform-application`
**Statut** : WAITING
**Priorité** : P0
**Bloquée par** : ISSUE-012, ISSUE-007
**Estime** : M

---

## Objectif

Créer les ports sortants (driven) : `ExecutionRepository`, `AgentRegistryPort`,
`ReportPublisherPort`.

## Fichiers à Créer

```
platform-application/src/main/java/com/performance/platform/application/ports/out/
  ├── ExecutionRepository.java
  ├── AgentRegistryPort.java
  └── ReportPublisherPort.java

platform-application/src/test/java/com/performance/platform/application/ports/out/
  └── PortsCompileTest.java — vérifie que les signatures compilent (mock/no-op)
```

## Interfaces à Implémenter

```java
public interface ExecutionRepository {
    void save(ExecutionState state);
    Optional<ExecutionState> findById(ExecutionId id);
    void updatePhase(ExecutionId id, Phase phase, PhaseStatus status);
    void saveTaskResult(ExecutionId id, TaskId taskId, AgentId agentId, TaskResult result);
    Map<AgentId, TaskResult> getTaskResults(ExecutionId id, TaskId taskId);
}

public interface AgentRegistryPort {
    void onAgentRegistered(AgentDescriptor descriptor);
    void onAgentHeartbeat(AgentId agentId, AgentHeartbeat heartbeat);
    void onAgentExpired(AgentId agentId);
    void onAgentDeregistered(AgentId agentId);
    List<AgentDescriptor> findByTaskName(String taskName);
    boolean hasAgentFor(String taskName);
    Optional<AgentDescriptor> findById(AgentId agentId);
    List<AgentDescriptor> findAll();
}

public interface ReportPublisherPort {
    void publish(ReportId reportId, ExecutionId executionId);
}
```

## Règles Spécifiques

- `saveTaskResult` doit supporter N appels pour la même task (multi-claim ADR-011).
- `findByTaskName` ne fait AUCUNE sélection — retourne tous les agents compétents.
- 0 annotation Spring.

## Critères de Done

- [ ] `mvn test -pl platform-application -q` → 0 erreur
- [ ] Les 3 ports compilent et sont mockables
- [ ] `progress.md` mis à jour : ISSUE-013 → DONE
- [ ] `context/interfaces-registry.md` : `ExecutionRepository`, `AgentRegistryPort`, `ReportPublisherPort` → STABLE
