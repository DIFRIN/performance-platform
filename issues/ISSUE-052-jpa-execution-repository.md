# ISSUE-052 — JpaExecutionRepository (adapter du port ExecutionRepository)

**PDR** : PDR-012
**Module** : `platform-infrastructure`
**Statut** : WAITING
**Priorité** : P1
**Bloquée par** : ISSUE-051, ISSUE-013
**Estime** : M

---

## Objectif

Implémenter `JpaExecutionRepository` (adapter du port `ExecutionRepository`) avec les
repositories Spring Data.

## Fichiers à Créer

```
platform-infrastructure/src/main/java/com/performance/platform/infrastructure/persistence/
  ├── JpaExecutionRepository.java
  ├── ExecutionStateJpaRepository.java
  └── TaskResultJpaRepository.java

platform-infrastructure/src/test/java/com/performance/platform/infrastructure/persistence/
  └── JpaExecutionRepositoryIT.java   — Testcontainers : save/find/multi-claim
```

## Interfaces à Implémenter

```java
@Repository
public class JpaExecutionRepository implements ExecutionRepository {
    public void save(ExecutionState state) { /* ... */ }
    public Optional<ExecutionState> findById(ExecutionId id) { /* ... */ }
    public void updatePhase(ExecutionId id, Phase phase, PhaseStatus status) { /* ... */ }
    public void saveTaskResult(ExecutionId id, TaskId taskId, AgentId agentId, TaskResult result) { /* ... */ }
    public Map<AgentId, TaskResult> getTaskResults(ExecutionId id, TaskId taskId) { /* ... */ }
}

interface ExecutionStateJpaRepository extends JpaRepository<ExecutionStateEntity, String> {}
interface TaskResultJpaRepository extends JpaRepository<TaskResultEntity, TaskResultId> {
    List<TaskResultEntity> findByExecutionIdAndTaskId(String executionId, String taskId);
}
```

## Règles Spécifiques

- `saveTaskResult` upsert par PK composite — appelable N fois pour une task (multi-claim).
- `save` idempotent (re-save même id = update).
- `getTaskResults` retourne `Map<AgentId, TaskResult>`.
- Accès DB sous Virtual Threads.

## Critères de Done

- [ ] `mvn verify -pl platform-infrastructure -P integration-tests` → IT passe
- [ ] save/find round-trip ; 2 saveTaskResult agents différents → getTaskResults retourne 2 entrées
- [ ] re-save même id → pas de doublon
- [ ] `progress.md` mis à jour : ISSUE-052 → DONE
- [ ] `context/interfaces-registry.md` : `JpaExecutionRepository` → STABLE
