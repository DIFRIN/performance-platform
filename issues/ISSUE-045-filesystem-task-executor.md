# ISSUE-045 — FilesystemTaskExecutor

**PDR** : PDR-010
**Module** : `platform-infrastructure`
**Statut** : WAITING
**Priorité** : P2
**Bloquée par** : ISSUE-039
**Estime** : S

---

## Objectif

Implémenter `FilesystemTaskExecutor` : CREATE/DELETE/UPLOAD/CLEANUP de fichiers et répertoires.

## Fichiers à Créer

```
platform-infrastructure/src/main/java/com/performance/platform/infrastructure/executor/fs/
  └── FilesystemTaskExecutor.java

platform-infrastructure/src/test/java/com/performance/platform/infrastructure/executor/fs/
  └── FilesystemTaskExecutorTest.java — CREATE/DELETE sur @TempDir
```

## Interfaces à Implémenter

```java
@Preparation(name = "filesystem", description = "FS create/delete/upload/cleanup")
public class FilesystemTaskExecutor implements TaskExecutor {
    public String getSupportedTaskName() { return "filesystem"; }
    public TaskResult execute(ExecutionContext context, StepDefinition step) { /* ... */ }
}
```

## Règles Spécifiques

- Params : `operation` (CREATE/DELETE/UPLOAD/CLEANUP), `path`, `source` (UPLOAD), `recursive`.
- Outputs : `{ "path": "...", "filesAffected": N }`.
- Échec → `TaskResult.failed`. I/O sous Virtual Threads.

## Critères de Done

- [ ] `mvn test -pl platform-infrastructure -q` → 0 erreur
- [ ] CREATE puis DELETE récursif testés sur `@TempDir`
- [ ] `progress.md` mis à jour : ISSUE-045 → DONE
- [ ] `context/interfaces-registry.md` : `FilesystemTaskExecutor` → STABLE
