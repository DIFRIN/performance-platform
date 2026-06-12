# PDR-003 — Plugin API

**Module Maven** : `platform-plugin-api`
**Package** : `com.performance.platform.plugin`
**Statut** : WAITING
**Specs de référence** : `specifications/03-task-framework.md` §1, §7.2, §7.8, `constraints.md` CF-07, CF-08
**Dépend de** : PDR-001
**Issues** : ISSUE-010, ISSUE-011

---

## Responsabilité

Module Maven léger publié aux développeurs de plugins externes. Contient UNIQUEMENT
le contrat public stable : l'interface `TaskExecutor`, les trois annotations
(`@Preparation`, `@Injection`, `@Assertion`) et l'interface `AssertionExecutor`.
Réexporte (dépend de) les records de `platform-domain` nécessaires (`TaskExecutor`
manipule `ExecutionContext`, `StepDefinition`, `TaskResult`).

**Contrainte absolue** : 0 dépendance Spring. Interface stable — toute modification = ADR + version majeure.
`TaskType` enum n'existe PAS : le type est le `String taskName`.

---

## Interfaces Publiques

### Annotations

```java
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface Preparation {
    String name();
    String version() default "1.0.0";
    String description() default "";
}

@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface Injection {
    String name();
    String version() default "1.0.0";
    String description() default "";
}

@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface Assertion {
    String name();
    String version() default "1.0.0";
    String description() default "";
}
```

### Interfaces d'extension

```java
public interface TaskExecutor {
    /**
     * Exécute la task. NE JAMAIS lever d'exception pour un échec métier
     * → retourner TaskResult.failed(). Exception uniquement pour erreur technique
     * irrécupérable.
     */
    TaskResult execute(ExecutionContext context, StepDefinition step);

    /**
     * Nom de la task supportée. Doit correspondre au name de l'annotation.
     * Remplace getSupportedType() — plus de TaskType enum.
     */
    String getSupportedTaskName();
}

public interface AssertionExecutor {
    AssertionResult evaluate(ExecutionContext context, StepDefinition step);
    String getSupportedAssertionName();   // correspond à @Assertion(name="...")
}
```

---

## Règles de Comportement

- Tout `TaskExecutor` (interne ou externe) doit porter exactement une des trois annotations.
- Le `name` de l'annotation est la clé de résolution DSL — doit être unique par phase.
- `getSupportedTaskName()` doit retourner la même valeur que `@Xxx(name=...)`.
- Aucun import Spring autorisé dans ce module (ArchUnit).
- Les classes de plugin externes doivent avoir un constructeur no-arg.

---

## Dépendances Techniques

```
Ce PDR utilise :
  PDR-001 → ExecutionContext, StepDefinition, TaskResult, AssertionResult, TaskId

Ce PDR est utilisé par :
  PDR-010 (task executors)   → implémentent TaskExecutor + annotations
  PDR-011 (plugin system)    → scanne les annotations
  PDR-013 (gatling)          → @Injection(name="gatling")
  PDR-014 (assertion)        → implémentent AssertionExecutor / TaskExecutor
```

---

## Critères de Done (PDR complet)

- [ ] Toutes les Issues du PDR sont DONE
- [ ] ArchUnit : 0 import Spring dans `platform-plugin-api`
- [ ] Annotations `RUNTIME` retention vérifiées par test réflexion
- [ ] `@Preparation/@Injection/@Assertion` dans `context/interfaces-registry.md` STABLE
