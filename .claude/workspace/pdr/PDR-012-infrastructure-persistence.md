# PDR-012 — Persistence (Infrastructure)

**Module Maven** : `platform-infrastructure`
**Package** : `com.performance.platform.infrastructure.persistence`
**Statut** : WAITING
**Specs de référence** : `.claude/knowledge/specs/02-execution-engine.md` §2, `.claude/knowledge/constraints.md` CNF-02, ADR-011
**Dépend de** : PDR-001, PDR-004
**Issues** : ISSUE-050, ISSUE-051, ISSUE-052, ISSUE-053

---

## Responsabilité

Implémente le port sortant `ExecutionRepository` avec JPA/PostgreSQL : entities, mappers
domain↔entity, repositories Spring Data, et l'adapter `JpaExecutionRepository`. Supporte
le multi-claim (un task result par agent, ADR-011) et le checkpointing par phase. Les
records du domaine restent purs : le mapping vit ici.

**Séparation stricte** : tout dans `com.performance.platform.infrastructure.persistence/`.
Aucune annotation JPA dans `platform-domain`.

---

## Interfaces Publiques

```java
@Repository
public class JpaExecutionRepository implements ExecutionRepository {
    public void save(ExecutionState state) { /* ... */ }
    public Optional<ExecutionState> findById(ExecutionId id) { /* ... */ }
    public void updatePhase(ExecutionId id, Phase phase, PhaseStatus status) { /* ... */ }
    public void saveTaskResult(ExecutionId id, TaskId taskId, AgentId agentId, TaskResult result) { /* ... */ }
    public Map<AgentId, TaskResult> getTaskResults(ExecutionId id, TaskId taskId) { /* ... */ }
}
```

### Entities (JPA) — internes au package

```java
@Entity @Table(name = "execution_state")
class ExecutionStateEntity { /* id, scenarioId, status, startedAt, updatedAt, phasesJson, contextJson */ }

@Entity @Table(name = "task_result")
class TaskResultEntity { /* executionId, taskId, agentId, status, outputsJson, completedAt — PK composite (executionId, taskId, agentId) */ }

interface ExecutionStateJpaRepository extends JpaRepository<ExecutionStateEntity, String> {}
interface TaskResultJpaRepository extends JpaRepository<TaskResultEntity, TaskResultId> {
    List<TaskResultEntity> findByExecutionIdAndTaskId(String executionId, String taskId);
}
```

### Mapper

```java
@Component
class ExecutionStateMapper {
    ExecutionStateEntity toEntity(ExecutionState state);
    ExecutionState toDomain(ExecutionStateEntity entity);
}
```

---

## Règles de Comportement

- PK composite `(executionId, taskId, agentId)` sur `task_result` → multi-claim supporté (ADR-011).
- `saveTaskResult()` appelé N fois pour une même task (un par agent) : upsert par PK composite.
- `outputs`, `context`, `phaseStatuses` sérialisés JSON (colonne `jsonb` PostgreSQL).
- Idempotence : re-save d'un `ExecutionState` avec même id = update, pas de doublon (CNF-02).
- Migrations gérées par Flyway (`db/migration`).
- Aucune entity exposée hors du package `.persistence` — toujours retourner des records domaine.
- Tout accès DB sous Virtual Threads.

---

## Dépendances Techniques

```
Ce PDR utilise :
  PDR-001 → ExecutionState, ExecutionId, TaskId, AgentId, TaskResult, Phase, PhaseStatus
  PDR-004 → ExecutionRepository (port implémenté)

Ce PDR est utilisé par :
  PDR-006 (execution engine) → checkpoint + lecture des résultats
```

---

## Critères de Done (PDR complet)

- [ ] Toutes les Issues du PDR sont DONE
- [ ] Test d'intégration Testcontainers PostgreSQL : save/find/multi-claim
- [ ] ArchUnit : annotations JPA confinées au package `.persistence`
- [ ] `JpaExecutionRepository` dans `.claude/context/interfaces-registry.md` STABLE
