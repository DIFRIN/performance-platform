# ISSUE-043 — ShellTaskExecutor

**PDR** : PDR-010
**Module** : `platform-infrastructure`
**Statut** : WAITING
**Priorité** : P2
**Bloquée par** : ISSUE-039
**Estime** : M

---

## Objectif

Implémenter `ShellTaskExecutor` : exécute une commande shell avec args, env, working dir,
timeout, exit codes attendus.

## Fichiers à Créer

```
platform-infrastructure/src/main/java/com/performance/platform/infrastructure/executor/shell/
  └── ShellTaskExecutor.java

platform-infrastructure/src/test/java/com/performance/platform/infrastructure/executor/shell/
  └── ShellTaskExecutorTest.java — echo → stdout, exit code non-zéro → failed
```

## Interfaces à Implémenter

```java
@Preparation(name = "shell", description = "Shell command execution")
public class ShellTaskExecutor implements TaskExecutor {
    public String getSupportedTaskName() { return "shell"; }
    public TaskResult execute(ExecutionContext context, StepDefinition step) { /* ... */ }
}
```

## Règles Spécifiques

- Params : `command`, `args` (List), `workingDirectory`, `env` (Map), `timeout`, `successExitCodes` (default [0]).
- Outputs : `{ "exitCode": N, "stdout": "...", "stderr": "..." }`.
- Exit code hors `successExitCodes` → `TaskResult.failed`.
- Implémente `StatefulResourceCleaner` (tuer le process fils si vivant).
- Process I/O sous Virtual Threads ; respecter `timeout`.

## Critères de Done

- [ ] `mvn test -pl platform-infrastructure -q` → 0 erreur
- [ ] `echo hello` → `stdout` contient "hello", exitCode 0
- [ ] exit code non attendu → `TaskResult.failed`
- [ ] `.claude/progress.md` mis à jour : ISSUE-043 → DONE
- [ ] `.claude/context/interfaces-registry.md` : `ShellTaskExecutor` → STABLE
