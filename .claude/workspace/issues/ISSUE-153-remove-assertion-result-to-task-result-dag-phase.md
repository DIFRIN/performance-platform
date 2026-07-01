# ISSUE-153 -- Remove assertionResultToTaskResult from DagPhaseExecutor

**PDR** : PDR-036
**Module** : `platform-execution-engine`
**Statut** : WAITING
**Priorite** : P2 (normal -- nettoie le code legacy apres migration)
**Bloquee par** : ISSUE-151 (AssertionResultMapper)
**Estime** : S (< 1h)

---

## Objectif

Remplacer la methode privee `DagPhaseExecutor.assertionResultToTaskResult()` par un appel a `AssertionResultMapper.toTaskResult()`. La methode dupliquee est supprimee. Aucun changement de comportement.

## Fichiers a Modifier

```
platform-execution-engine/src/main/java/com/performance/platform/engine/local/
  └── DagPhaseExecutor.java   -- remplacer assertionResultToTaskResult() par appel a AssertionResultMapper

platform-execution-engine/src/test/java/com/performance/platform/engine/local/
  └── DagPhaseExecutorTest.java -- verifier que la conversion AssertionResult->TaskResult utilise AssertionResultMapper
```

## Regles Specifiques

- La methode `assertionResultToTaskResult()` est SUPPRIMEE (pas depreciee)
- Le code de `executeAssertionStep()` est modifie pour appeler `AssertionResultMapper.toTaskResult(assertionResult, stepDef)` au lieu de `assertionResultToTaskResult(assertionResult, stepDef)`
- Verifier que le comportement est identique : memes statuts, memes outputs, meme errorMessage
- Si `AssertionResultMapper` n'est pas encore cree (ISSUE-151 pas DONE), cette Issue est bloquee

## Criteres de Done

- [ ] `mvn test -pl platform-execution-engine -q` -> 0 erreur
- [ ] `assertionResultToTaskResult()` est supprimee de `DagPhaseExecutor`
- [ ] Le code appelle `AssertionResultMapper.toTaskResult()` a la place
- [ ] Tous les tests existants de `DagPhaseExecutor` passent sans modification
- [ ] `.claude/workspace/progress.md` mis a jour : ISSUE-153 -> DONE
