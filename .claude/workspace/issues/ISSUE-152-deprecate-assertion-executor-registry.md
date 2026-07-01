# ISSUE-152 -- Deprecate AssertionExecutorRegistry + Unify Spring Bean Collection

**PDR** : PDR-036
**Module** : `platform-assertion`, `platform-infrastructure`
**Statut** : WAITING
**Priorite** : P2 (normal -- les registres co-existent, pas de regression fonctionnelle)
**Bloquee par** : ISSUE-150 (AssertionExecutor extends TaskExecutor)
**Estime** : M (1-2h)

---

## Objectif

Apres `AssertionExecutor extends TaskExecutor` (ISSUE-150), les 6 implementations d'assertion sont automatiquement des beans `TaskExecutor`. Cette issue :
1. Verifie que `DefaultTaskExecutorRegistry` collecte bien les `AssertionExecutor` beans (test d'integration Spring)
2. Deprecie `AssertionExecutorRegistry` et `DefaultAssertionExecutorRegistry` sans les supprimer
3. S'assure que le `TaskExecutorLookup` peut resoudre les assertions via `findTaskExecutor()`

## Fichiers a Modifier

```
platform-assertion/src/main/java/com/performance/platform/assertion/
  ├── AssertionExecutorRegistry.java          -- ajouter @Deprecated(since="2.0", forRemoval=true)
  └── DefaultAssertionExecutorRegistry.java   -- ajouter @Deprecated(since="2.0", forRemoval=true)

platform-assertion/src/test/java/com/performance/platform/assertion/
  └── AssertionExecutorAsTaskExecutorTest.java -- NOUVEAU: test Spring verifiant que les 6 AssertionExecutor sont dans DefaultTaskExecutorRegistry

platform-infrastructure/src/test/java/com/performance/platform/infrastructure/executor/
  └── AssertionExecutorsInTaskRegistryTest.java -- NOUVEAU: test Spring verifiant collection auto par DefaultTaskExecutorRegistry
```

## Regles Specifiques

- Ne PAS supprimer `AssertionExecutorRegistry` -- uniquement deprecation. Du code legacy (ex: `PluginLoader`) peut encore l'utiliser.
- Le test Spring doit charger un contexte avec `DefaultTaskExecutorRegistry` et verifier que `getSupportedTaskNames()` contient les 6 noms d'assertion : `gatling-metric`, `database`, `kafka`, `wiremock`, `http-mock`, `file`.
- Le test doit verifier que `registry.getFor("gatling-metric")` retourne une instance de `GatlingMetricAssertionExecutor`.
- Si `DefaultTaskExecutorRegistry` ne collecte PAS automatiquement les `AssertionExecutor` (parce que le constructeur prend `List<TaskExecutor>` et que Spring ne les voit pas comme `TaskExecutor` sans recompilation), alors il faut ajuster le constructeur pour prendre aussi `List<AssertionExecutor>` et les enregistrer. Dans ce cas, documenter le changement dans le code et dans l'Issue.

## Criteres de Done

- [ ] `mvn test -pl platform-assertion -q` -> 0 erreur
- [ ] `mvn test -pl platform-infrastructure -q` -> 0 erreur
- [ ] `AssertionExecutorRegistry` a l'annotation `@Deprecated(since="2.0", forRemoval=true)`
- [ ] `DefaultAssertionExecutorRegistry` a l'annotation `@Deprecated(since="2.0", forRemoval=true)`
- [ ] Test : `DefaultTaskExecutorRegistry.getSupportedTaskNames()` contient les 6 noms d'assertion
- [ ] Test : `DefaultTaskExecutorRegistry.getFor("database")` retourne `DatabaseAssertionExecutor`
- [ ] Les 6 executors sont resolubles a la fois via `TaskExecutorRegistry` ET `AssertionExecutorRegistry`
- [ ] `.claude/workspace/progress.md` mis a jour : ISSUE-152 -> DONE
- [ ] `.claude/workspace/interfaces-registry.md` mis a jour : AssertionExecutorRegistry, DefaultAssertionExecutorRegistry -> DEPRECATED
