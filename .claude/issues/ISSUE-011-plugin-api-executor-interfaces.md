# ISSUE-011 — Interfaces TaskExecutor / AssertionExecutor + ArchUnit no-Spring

**PDR** : PDR-003
**Module** : `platform-plugin-api`
**Statut** : WAITING
**Priorité** : P0
**Bloquée par** : ISSUE-010, ISSUE-006
**Estime** : S

---

## Objectif

Créer les interfaces d'extension publiques `TaskExecutor` et `AssertionExecutor`, plus un
test ArchUnit garantissant 0 import Spring dans le module.

## Fichiers à Créer

```
platform-plugin-api/src/main/java/com/performance/platform/plugin/
  ├── TaskExecutor.java
  └── AssertionExecutor.java

platform-plugin-api/src/test/java/com/performance/platform/plugin/
  └── PluginApiArchitectureTest.java — ArchUnit : 0 import org.springframework
```

## Interfaces à Implémenter

```java
public interface TaskExecutor {
    /** Échec métier → TaskResult.failed(). Exception uniquement pour erreur technique. */
    TaskResult execute(ExecutionContext context, StepDefinition step);
    /** Doit correspondre au name de l'annotation. Remplace getSupportedType(). */
    String getSupportedTaskName();
}

public interface AssertionExecutor {
    AssertionResult evaluate(ExecutionContext context, StepDefinition step);
    String getSupportedAssertionName();   // correspond à @Assertion(name="...")
}
```

## Règles Spécifiques

- `getSupportedTaskName()` retourne `String` — JAMAIS `TaskType`.
- ArchUnit : interdire `org.springframework..` dans `com.performance.platform.plugin..`.

## Critères de Done

- [ ] `mvn test -pl platform-plugin-api -q` → 0 erreur
- [ ] Test ArchUnit passe
- [ ] Aucune occurrence de `TaskType` ou `getSupportedType`
- [ ] `.claude/progress.md` mis à jour : ISSUE-011 → DONE
- [ ] `.claude/context/interfaces-registry.md` : `TaskExecutor`, `AssertionExecutor` → STABLE
