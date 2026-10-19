# ISSUE-189 — Deterministic Result Selection Policy (T6)

**PDR** : PDR-051
**Module** : `platform-execution-engine`
**Statut** : WAITING
**Priorité** : P2
**Bloquée par** : ISSUE-185
**Estime** : M (1-3h)

---

## Objectif

Rendre la sélection multi-claim déterministe (B2) : remplacer
`results.values().stream().filter(isSuccess).findFirst()` (ordre HashMap) par une
politique explicite basée sur l'ordre de complétion enregistré par le tracker.

> ADR requise (ADR-034).

## Fichiers à Modifier

```
platform-execution-engine/src/main/java/com/performance/platform/engine/
  ├── correlation/ResultSelectionPolicy.java      — NOUVEAU (enum)
  ├── correlation/TaskCorrelationTracker.java     — ajouter selectResult
  ├── correlation/DefaultTaskCorrelationTracker.java — implémenter selectResult
  ├── remote/RemoteStepDispatcher.java            — utiliser selectResult
  └── config/ExecutionEngineProperties.java       — champ execution.resultSelectionPolicy

platform-execution-engine/src/test/java/com/performance/platform/engine/
  └── ResultSelectionDeterminismTest.java         — NOUVEAU (T6)
```

## Interfaces à Implémenter

```java
public enum ResultSelectionPolicy { FIRST_COMPLETED, LAST_COMPLETED }
Optional<TaskResult> selectResult(MessageId messageId, ResultSelectionPolicy policy); // tracker
```

## Règles Spécifiques

- `FIRST_COMPLETED` = premier dans `resultsInCompletionOrder` (séquence atomique), jamais l'ordre d'itération d'une map.
- T6 : 1 000 runs → même agent sélectionné.

## Critères de Done

- [ ] `ResultSelectionDeterminismTest` (T6) vert 1 000/1 000
- [ ] `mvn test -pl platform-execution-engine -q` → 0 erreur
- [ ] `.claude/workspace/progress.md` mis à jour : ISSUE-189 → DONE
- [ ] `.claude/workspace/interfaces-registry.md` mis à jour (ResultSelectionPolicy)
