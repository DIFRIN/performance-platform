# ISSUE-040 — DatabaseTaskExecutor (@Preparation name="database")

**PDR** : PDR-010
**Module** : `platform-infrastructure`
**Statut** : WAITING
**Priorité** : P1
**Bloquée par** : ISSUE-039
**Estime** : M

---

## Objectif

Implémenter `DatabaseTaskExecutor` : opérations DB (PURGE/POPULATE/MIGRATION/BACKUP/RESTORE)
référençant une datasource configurée par nom.

## Fichiers à Créer

```
platform-infrastructure/src/main/java/com/performance/platform/infrastructure/executor/database/
  ├── DatabaseTaskExecutor.java
  └── DatasourceProvider.java   — résout une datasource par nom (config globale)

platform-infrastructure/src/test/java/com/performance/platform/infrastructure/executor/database/
  └── DatabaseTaskExecutorIT.java — Testcontainers PostgreSQL : PURGE/POPULATE
```

## Interfaces à Implémenter

```java
@Preparation(name = "database", description = "DB operations: purge, populate, migrate")
public class DatabaseTaskExecutor implements TaskExecutor {
    public String getSupportedTaskName() { return "database"; }
    public TaskResult execute(ExecutionContext context, StepDefinition step) { /* ... */ }
}
```

## Règles Spécifiques

- Paramètres : `operation`, `datasource`, `table` (PURGE), `scriptPath` (POPULATE), `timeout`.
- Outputs : `{ "rowsAffected": N, "duration": "X.Xs" }`.
- Échec → `TaskResult.failed(step.id(), "database", elapsed, msg, cause)` — pas d'exception métier.
- `TaskResult.success(step.id(), "database", elapsed, outputs)` — `String taskName`.
- Implémente aussi `StatefulResourceCleaner` (rollback transaction, fermer connexion).
- I/O sous Virtual Threads.

## Critères de Done

- [ ] `mvn verify -pl platform-infrastructure -P integration-tests` → IT passe
- [ ] PURGE sur PostgreSQL retourne `rowsAffected`
- [ ] Échec DB → `TaskResult.failed` (pas d'exception)
- [ ] `.claude/progress.md` mis à jour : ISSUE-040 → DONE
- [ ] `.claude/context/interfaces-registry.md` : `DatabaseTaskExecutor` → STABLE
