# ISSUE-154 -- Unify DagPhaseExecutor Assertion Execution Path

**PDR** : PDR-037
**Module** : `platform-execution-engine`
**Statut** : APPROVED
**Priorite** : P1 (critique -- simplification du code, reduction duplication)
**Bloquee par** : ISSUE-150 (AssertionExecutor extends TaskExecutor), ISSUE-153 (AssertionResultMapper remplace assertionResultToTaskResult)
**Estime** : M (1-3h)

---

## Objectif

Simplifier `DagPhaseExecutor` en supprimant la branche conditionnelle `phase == Phase.ASSERTION`. Avec `AssertionExecutor extends TaskExecutor`, toutes les phases utilisent le meme chemin de resolution et d'execution.

## Fichiers a Modifier

```
platform-execution-engine/src/main/java/com/performance/platform/engine/local/
  └── DagPhaseExecutor.java   -- remplacer executeSingleStep() (branche if/else phase) par chemin unifie

platform-execution-engine/src/test/java/com/performance/platform/engine/local/
  └── DagPhaseExecutorTest.java -- ajouter/modifier tests pour couvrir le chemin unifie avec assertions
```

## Regles Specifiques

Le changement principal est dans `executeSingleStep()`. Remplacer :

```java
// AVANT (a supprimer) :
if (phase == Phase.ASSERTION) {
    result = executeAssertionStep(stepDef, context, lookup, policy);
} else {
    result = executePreparationOrInjectionStep(stepDef, context, lookup, policy);
}
```

Par :

```java
// APRES :
TaskExecutor executor = lookup.findTaskExecutor(stepDef.taskName());
if (executor == null) {
    return new StepExecutionResult(stepDef.id(),
        TaskResult.failed(stepDef.id(), stepDef.taskName(),
            Duration.ZERO, "No TaskExecutor found for taskName: " + stepDef.taskName(), null));
}
result = retryExecutor.executeWithRetry(policy, () -> executor.execute(context, stepDef));
```

- Les methodes `executeAssertionStep()` et `executePreparationOrInjectionStep()` sont SUPPRIMEES
- La nouvelle methode s'appelle `executeStep()` (nom plus court, pas de distinction de phase)
- Le parametre `Phase phase` n'est plus necessaire dans `executeSingleStep()` ni dans `executeStep()`
- Verifier que le retry fonctionne avec les assertions (si un `AssertionExecutor.execute()` retourne `TaskResult.failed()`, le retry doit re-executer)

## Criteres de Done

- [ ] `mvn test -pl platform-execution-engine -q` -> 0 erreur
- [ ] `executeAssertionStep()` supprimee
- [ ] `executePreparationOrInjectionStep()` supprimee
- [ ] Chemin unifie `executeStep()` implemente
- [ ] Test : assertion step via `findTaskExecutor()` resout un `AssertionExecutor` et appelle `execute()`
- [ ] Test : assertion step avec `execute()` retournant SUCCESS -> resultat stocke dans ExecutionContext
- [ ] Test : assertion step avec `execute()` retournant FAILED -> marque comme echec, retry si configure
- [ ] Test : step non-trouve (null executor) -> TaskResult.failed avec message explicite
- [ ] Test : toutes les phases (PREPARATION, INJECTION, ASSERTION) passent par le meme chemin
- [ ] `.claude/workspace/progress.md` mis a jour : ISSUE-154 -> DONE
