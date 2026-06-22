# ISSUE-057 — GatlingTaskExecutor (@Injection name="gatling")

**PDR** : PDR-013
**Module** : `platform-injection-gatling`
**Statut** : WAITING
**Priorité** : P1
**Bloquée par** : ISSUE-055, ISSUE-056
**Estime** : M

---

## Objectif

Implémenter `GatlingTaskExecutor` : assemble runner + parser, retourne `InjectionResult` dans
`TaskResult.outputs` sous la clé `"result"`.

## Fichiers à Créer

```
platform-injection-gatling/src/main/java/com/performance/platform/injection/gatling/
  └── GatlingTaskExecutor.java

platform-injection-gatling/src/test/java/com/performance/platform/injection/gatling/
  └── GatlingTaskExecutorTest.java
```

## Interfaces à Implémenter

```java
@Injection(name = "gatling", description = "Gatling load injection")
public class GatlingTaskExecutor implements TaskExecutor {
    public String getSupportedTaskName() { return "gatling"; }
    public TaskResult execute(ExecutionContext context, StepDefinition step) {
        // 1. extraire simulation + loadModel depuis step.parameters()
        // 2. GatlingRunner.run(...)
        // 3. GatlingResultParser.parse(...)
        // 4. return TaskResult.success(step.id(), "gatling", elapsed, Map.of("result", injectionResult));
    }
}
```

## Règles Spécifiques

- `getSupportedTaskName()` == `"gatling"` (String).
- `TaskResult.success(step.id(), "gatling", elapsed, Map.of("result", injectionResult))` — `String taskName`.
- Échec d'exécution → `TaskResult.failed(...)`, jamais d'exception métier propagée.
- Implémente `StatefulResourceCleaner` (arrêt simulation en cours).

## Critères de Done

- [ ] `mvn test -pl platform-injection-gatling -q` → 0 erreur
- [ ] `execute` retourne un `TaskResult` SUCCESS avec `outputs["result"]` = `InjectionResult`
- [ ] Aucune occurrence de `TaskType`
- [ ] `.claude/progress.md` mis à jour : ISSUE-057 → DONE
- [ ] `.claude/context/interfaces-registry.md` : `GatlingTaskExecutor` → STABLE
