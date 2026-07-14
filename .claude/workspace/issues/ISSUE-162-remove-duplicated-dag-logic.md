# ISSUE-162 — Remove Duplicated DAG Logic from RemoteExecutionEngine

**PDR** : PDR-039
**Module** : `platform-execution-engine`
**Statut** : DONE
**Priorite** : P2 (cleanup après ISSUE-161)
**Bloquee par** : ISSUE-161 (RemoteExecutionEngine utilise déjà DagPhaseExecutor)
**Estime** : S (< 1h)

---

## Objectif

Supprimer les méthodes de parcours DAG dupliquées dans `RemoteExecutionEngine` après que ISSUE-161 a migré l'engine vers `DagPhaseExecutor` partagé. Vérifier qu'aucune régression.

## Fichiers à Modifier

```
platform-execution-engine/src/main/java/com/performance/platform/engine/remote/
  └── RemoteExecutionEngine.java   — supprimer les méthodes DAG dupliquées
```

## Méthodes à Supprimer

Les méthodes suivantes sont supprimées de `RemoteExecutionEngine` :

1. `groupStepsByLevel(List<ExecutionStep>)` — remplacée par `DagPhaseExecutor.groupStepsByLevel()`
2. `classifySteps(List<ExecutionStep>, ExecutionContext)` — remplacée par `DagPhaseExecutor.classifySteps()`
3. `LevelClassification` (record interne) — remplacé par celui du `DagPhaseExecutor`
4. `allDependenciesSatisfied(ExecutionStep, ExecutionContext)` — remplacée par `DagPhaseExecutor.allDependenciesSatisfied()`
5. `dispatchAndWaitLevel(...)` — remplacée par `DagPhaseExecutor.executeLevel()` + `RemoteStepDispatcher`
6. `LevelResult` (record interne) — remplacé par `DagPhaseExecutor.LevelResult` (ou le résultat est géré directement)

## Méthodes Conservées

Ces méthodes ont une sémantique différente en DISTRIBUTED (itèrent sur tous les résultats d'agents) et sont conservées :

7. `checkFailedInPhase(ExecutionContext, List<ExecutionStep>)` — vérifie TOUS les agents, pas seulement `LOCAL_AGENT`
8. `checkSkippedInAnyPhase(ExecutionContext, ExecutionPlan)` — idem
9. `checkSkippedInSteps(ExecutionContext, List<ExecutionStep>)` — idem

## Vérification

Compter les lignes supprimées et s'assurer que `mvn test -pl platform-execution-engine -q` passe.

## Criteres de Done

- [ ] 6 méthodes DAG dupliquées supprimées de `RemoteExecutionEngine`
- [ ] ~120 lignes de code supprimées
- [ ] `mvn test -pl platform-execution-engine -q` → 0 erreur
- [ ] `mvn test -pl platform-execution-engine -q` → même nombre de tests qu'avant (0 supprimé)
