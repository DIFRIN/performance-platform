# ISSUE-155 -- Deprecate TaskExecutorLookup.findAssertionExecutor

**PDR** : PDR-037
**Module** : `platform-execution-engine`
**Statut** : DONE
**Priorite** : P2 (normal -- deprecation sans suppression)
**Bloquee par** : ISSUE-154 (DagPhaseExecutor unifie)
**Estime** : S (< 1h)

---

## Objectif

Deprecier `TaskExecutorLookup.findAssertionExecutor()` en fournissant une implementation `default` qui delegue a `findTaskExecutor()`. Apres PDR-035, `AssertionExecutor extends TaskExecutor`, donc la recherche est unifiee.

## Fichiers a Modifier

```
platform-execution-engine/src/main/java/com/performance/platform/engine/local/
  └── TaskExecutorLookup.java   -- ajouter default method findAssertionExecutor() avec @Deprecated

platform-execution-engine/src/test/java/com/performance/platform/engine/local/
  └── TaskExecutorLookupTest.java -- NOUVEAU: test de la default method findAssertionExecutor
```

## Interfaces a Implementer

```java
// Dans TaskExecutorLookup.java, ajouter :
/**
 * @deprecated Depuis PDR-035, utiliser {@link #findTaskExecutor(String)}
 *             qui resout aussi les assertions (AssertionExecutor extends TaskExecutor).
 */
@Deprecated(since = "2.0", forRemoval = true)
default AssertionExecutor findAssertionExecutor(String assertionName) {
    TaskExecutor executor = findTaskExecutor(assertionName);
    if (executor instanceof AssertionExecutor ae) {
        return ae;
    }
    return null;
}
```

## Regles Specifiques

- La methode par defaut est dans l'INTERFACE, pas dans les implementations
- Les implementations existantes de `TaskExecutorLookup` n'ont pas besoin de changer
- Si `findTaskExecutor()` retourne un executor qui n'est PAS un `AssertionExecutor` (ex: `DatabaseTaskExecutor` pour le nom "database"), `findAssertionExecutor` retourne null (ce qui est correct -- "database" comme assertion resout `DatabaseAssertionExecutor`, pas `DatabaseTaskExecutor`)
- Le nom "database" est ambigu : `DatabaseTaskExecutor` (PREPARATION) et `DatabaseAssertionExecutor` (ASSERTION) ont le meme `getSupportedTaskName()`. Ce conflit est gere par le registre (dernier enregistre gagne). Voir NOTE ci-dessous.

**NOTE IMPORTANTE -- conflit de nommage** : `DatabaseTaskExecutor` (PREPARATION, taskName="database") et `DatabaseAssertionExecutor` (ASSERTION, assertionName="database") partagent le meme nom. Avec le registre unifie, un seul survit. Solution : les assertions doivent avoir des noms DISTINCTS. `DatabaseAssertionExecutor.getSupportedAssertionName()` retourne deja "database". Soit on renomme l'un des deux, soit on accepte que le dernier enregistre gagne (comportement actuel du registre). **Cette ambiguite est resolue dans ISSUE-156.**

## Criteres de Done

- [ ] `mvn test -pl platform-execution-engine -q` -> 0 erreur
- [ ] `findAssertionExecutor()` a l'annotation `@Deprecated(since="2.0", forRemoval=true)`
- [ ] La methode default delegue a `findTaskExecutor()` et cast en `AssertionExecutor`
- [ ] Test : `findAssertionExecutor("gatling-metric")` via mock de `findTaskExecutor` retournant un `AssertionExecutor` -> cast reussi
- [ ] Test : `findAssertionExecutor("performance_test")` via mock de `findTaskExecutor` retournant un `TaskExecutor` non-AssertionExecutor -> retourne null
- [ ] `.claude/workspace/progress.md` mis a jour : ISSUE-155 -> DONE
