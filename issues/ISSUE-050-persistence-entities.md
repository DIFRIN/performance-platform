# ISSUE-050 — Entities JPA (ExecutionState / TaskResult) + migrations Flyway

**PDR** : PDR-012
**Module** : `platform-infrastructure`
**Statut** : WAITING
**Priorité** : P1
**Bloquée par** : ISSUE-006, ISSUE-013
**Estime** : M

---

## Objectif

Créer les entities JPA (package `.persistence`) et les migrations Flyway. PK composite sur
`task_result` pour supporter le multi-claim (ADR-011).

## Fichiers à Créer

```
platform-infrastructure/src/main/java/com/performance/platform/infrastructure/persistence/
  ├── ExecutionStateEntity.java
  ├── TaskResultEntity.java
  └── TaskResultId.java          — clé composite (executionId, taskId, agentId)
platform-infrastructure/src/main/resources/db/migration/
  ├── V1__execution_state.sql
  └── V2__task_result.sql

platform-infrastructure/src/test/java/com/performance/platform/infrastructure/persistence/
  └── EntitiesMappingIT.java     — Testcontainers : persist/read entity
```

## Schéma (résumé)

```sql
-- V1
CREATE TABLE execution_state (
  id VARCHAR PRIMARY KEY, scenario_id VARCHAR, status VARCHAR,
  phases JSONB, context JSONB, started_at TIMESTAMPTZ, updated_at TIMESTAMPTZ);
-- V2
CREATE TABLE task_result (
  execution_id VARCHAR, task_id VARCHAR, agent_id VARCHAR,
  status VARCHAR, outputs JSONB, completed_at TIMESTAMPTZ,
  PRIMARY KEY (execution_id, task_id, agent_id));
```

## Règles Spécifiques

- PK composite `(execution_id, task_id, agent_id)` → multi-claim (ADR-011).
- Colonnes JSON en `jsonb`.
- Annotations JPA confinées au package `.persistence` (jamais dans le domaine).
- Entities `package-private` (non exposées hors du package).

## Critères de Done

- [ ] `mvn verify -pl platform-infrastructure -P integration-tests` → IT passe
- [ ] Flyway applique V1 et V2 sur PostgreSQL Testcontainers
- [ ] Insertion de 2 résultats même task, agents différents → 2 lignes
- [ ] `progress.md` mis à jour : ISSUE-050 → DONE
