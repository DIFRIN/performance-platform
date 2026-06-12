# ISSUE-010 — Annotations @Preparation / @Injection / @Assertion

**PDR** : PDR-003
**Module** : `platform-plugin-api`
**Statut** : WAITING
**Priorité** : P0
**Bloquée par** : ISSUE-003, ISSUE-004
**Estime** : S

---

## Objectif

Créer le module `platform-plugin-api` (dépend de `platform-domain`) et ses trois
annotations de marquage des `TaskExecutor`, avec rétention RUNTIME.

## Fichiers à Créer

```
platform-plugin-api/pom.xml                 — dépend de platform-domain, 0 Spring
platform-plugin-api/src/main/java/com/performance/platform/plugin/
  ├── Preparation.java
  ├── Injection.java
  └── Assertion.java

platform-plugin-api/src/test/java/com/performance/platform/plugin/
  └── AnnotationsRetentionTest.java — réflexion : RUNTIME + @Target(TYPE)
```

## Interfaces à Implémenter

```java
@Target(ElementType.TYPE) @Retention(RetentionPolicy.RUNTIME) @Documented
public @interface Preparation { String name(); String version() default "1.0.0"; String description() default ""; }

@Target(ElementType.TYPE) @Retention(RetentionPolicy.RUNTIME) @Documented
public @interface Injection { String name(); String version() default "1.0.0"; String description() default ""; }

@Target(ElementType.TYPE) @Retention(RetentionPolicy.RUNTIME) @Documented
public @interface Assertion { String name(); String version() default "1.0.0"; String description() default ""; }
```

## Règles Spécifiques

- 0 dépendance Spring dans le `pom.xml` (scope provided autorisé seulement pour platform-domain en compile).
- `name()` sans valeur par défaut (obligatoire). `version`/`description` avec défauts.

## Critères de Done

- [ ] `mvn test -pl platform-plugin-api -q` → 0 erreur
- [ ] Réflexion : les 3 annotations ont `RetentionPolicy.RUNTIME` et `ElementType.TYPE`
- [ ] `pom.xml` ne contient aucune dépendance `spring-*`
- [ ] `progress.md` mis à jour : ISSUE-010 → DONE
- [ ] `context/interfaces-registry.md` : annotations → STABLE
