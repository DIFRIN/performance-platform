# ISSUE-004 — TaskResult avec String taskName

**PDR** : PDR-001
**Module** : `platform-domain`
**Statut** : WAITING
**Priorité** : P0
**Bloquée par** : ISSUE-001, ISSUE-002
**Estime** : S

---

## Objectif

Créer le record `TaskResult` immuable avec ses factories. **`String taskName` remplace
`TaskType taskType`** partout.

## Fichiers à Créer

```
platform-domain/src/main/java/com/performance/platform/domain/task/
  └── TaskResult.java

platform-domain/src/test/java/com/performance/platform/domain/task/
  └── TaskResultTest.java — success/failed/skipped, isSuccess(), copie défensive outputs
```

## Interfaces à Implémenter

```java
public record TaskResult(
    TaskId taskId,
    String taskName,              // remplace TaskType taskType
    TaskStatus status,
    Duration duration,
    Map<String, Object> outputs,
    String errorMessage,          // null si SUCCESS
    Throwable cause,              // null si SUCCESS
    Instant completedAt
) {
    public TaskResult {
        outputs = outputs == null ? Map.of() : Map.copyOf(outputs);
    }
    public static TaskResult success(TaskId id, String taskName, Duration duration, Map<String, Object> outputs) {
        return new TaskResult(id, taskName, TaskStatus.SUCCESS, duration, outputs, null, null, Instant.now());
    }
    public static TaskResult failed(TaskId id, String taskName, Duration duration, String message, Throwable cause) {
        return new TaskResult(id, taskName, TaskStatus.FAILED, duration, Map.of(), message, cause, Instant.now());
    }
    public static TaskResult skipped(TaskId id, String taskName, String reason) {
        return new TaskResult(id, taskName, TaskStatus.SKIPPED, Duration.ZERO, Map.of(), reason, null, Instant.now());
    }
    public boolean isSuccess() { return status == TaskStatus.SUCCESS; }
}
```

## Règles Spécifiques

- Le champ est `String taskName` — NE PAS créer ni importer `TaskType`.
- `outputs` copié défensivement.

## Critères de Done

- [ ] `mvn test -pl platform-domain -q` → 0 erreur
- [ ] `TaskResult.success(id, "gatling", d, Map.of("k","v")).isSuccess()` == true
- [ ] `TaskResult.failed(...).isSuccess()` == false
- [ ] Aucune occurrence de `TaskType` dans le fichier
- [ ] `progress.md` mis à jour : ISSUE-004 → DONE
- [ ] `context/interfaces-registry.md` : `TaskResult` → STABLE
