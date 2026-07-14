# ISSUE-150 -- AssertionExecutor extends TaskExecutor

**PDR** : PDR-035
**Module** : `platform-plugin-api`
**Statut** : DONE
**Priorite** : P0 (bloquant -- PDR-036 et PDR-037 dependent de cette evolution)
**Bloquee par** : ISSUE-148 (AssertionSummary -- reference par la default method execute())
**Estime** : M (1-2h)

---

## Objectif

Faire evoluer `AssertionExecutor` pour qu'il etende `TaskExecutor`. Ajouter une implementation par defaut de `execute()` qui appelle `evaluate()` et convertit le resultat en `TaskResult` avec `AssertionSummary` dans les outputs. Ajouter une implementation par defaut de `getSupportedTaskName()` qui delegue a `getSupportedAssertionName()`. Zero breaking change.

## Fichiers a Modifier

```
platform-plugin-api/src/main/java/com/performance/platform/plugin/
  └── AssertionExecutor.java     -- extends TaskExecutor, + execute() default, + getSupportedTaskName() default

platform-plugin-api/src/test/java/com/performance/platform/plugin/
  └── AssertionExecutorTest.java -- tests: execute() appelle evaluate(), getSupportedTaskName delegue, backward compat
```

## Interfaces a Implementer

```java
// Du PDR-035, a implementer EXACTEMENT :
// Modifier AssertionExecutor.java pour :
//   1. Changer "public interface AssertionExecutor" en "public interface AssertionExecutor extends TaskExecutor"
//   2. Ajouter la default method execute() (voir PDR-035 pour le code complet)
//   3. Ajouter la default method getSupportedTaskName() { return getSupportedAssertionName(); }

// TaskExecutor.java -- AUCUN changement
```

## Regles Specifiques

- La Javadoc doit documenter que c'est une evolution additive, pas un breaking change
- `execute()` default method utilise `AssertionSummary` -- verifier que le import compile dans `platform-plugin-api` (dependance vers `platform-domain` est autorisee)
- `toTaskResult()` est une methode `private` dans l'interface (Java 25 permet les private methods dans les interfaces)
- Les 6 implementations existantes doivent compiler sans modification apres ce changement

## Criteres de Done

- [ ] `mvn compile -pl platform-plugin-api -q` -> 0 erreur
- [ ] `mvn test -pl platform-plugin-api -q` -> 0 erreur
- [ ] `AssertionExecutor extends TaskExecutor` compile
- [ ] La methode `execute()` default est testee avec un mock d'AssertionExecutor :
  - Verifie que `execute()` appelle `evaluate()` et retourne un `TaskResult` avec `AssertionSummary` dans outputs
  - Verifie que `getSupportedTaskName()` retourne la meme valeur que `getSupportedAssertionName()`
- [ ] Test de backward compat : un stub implementant uniquement `evaluate()` et `getSupportedAssertionName()` compile et herite `execute()`
- [ ] Les 6 implementations d'assertion existantes compilent sans modification (verifier via `mvn compile -pl platform-assertion -q`)
- [ ] ArchUnit : 0 annotation framework dans `platform-plugin-api`
- [ ] `.claude/workspace/progress.md` mis a jour : ISSUE-150 -> DONE
- [ ] `.claude/workspace/interfaces-registry.md` mis a jour : AssertionExecutor -> STABLE (etend TaskExecutor)
