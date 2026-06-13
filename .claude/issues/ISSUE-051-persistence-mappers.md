# ISSUE-051 — Mappers domain ↔ entity

**PDR** : PDR-012
**Module** : `platform-infrastructure`
**Statut** : WAITING
**Priorité** : P1
**Bloquée par** : ISSUE-050
**Estime** : M

---

## Objectif

Implémenter les mappers entre records domaine et entities JPA (sérialisation JSON du store/outputs).

## Fichiers à Créer

```
platform-infrastructure/src/main/java/com/performance/platform/infrastructure/persistence/mapper/
  ├── ExecutionStateMapper.java
  └── TaskResultMapper.java

platform-infrastructure/src/test/java/com/performance/platform/infrastructure/persistence/mapper/
  ├── ExecutionStateMapperTest.java
  └── TaskResultMapperTest.java
```

## Interfaces à Implémenter

```java
@Component
class ExecutionStateMapper {
    ExecutionStateEntity toEntity(ExecutionState state);
    ExecutionState toDomain(ExecutionStateEntity entity);
}

@Component
class TaskResultMapper {
    TaskResultEntity toEntity(ExecutionId executionId, TaskId taskId, AgentId agentId, TaskResult result);
    TaskResult toDomain(TaskResultEntity entity);
}
```

## Règles Spécifiques

- `phaseStatuses`, `context.store`, `outputs` sérialisés/désérialisés JSON (Jackson).
- `TaskResult.taskName` mappé (String) — pas de `TaskType`.
- Round-trip domaine→entity→domaine doit préserver les données.

## Critères de Done

- [ ] `mvn test -pl platform-infrastructure -q` → 0 erreur
- [ ] Round-trip `ExecutionState` et `TaskResult` préserve les valeurs
- [ ] `.claude/progress.md` mis à jour : ISSUE-051 → DONE
