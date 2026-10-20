# PDR-051 — Distributed Completion Semantics (deterministic selection + signal)

**Module Maven** : `platform-execution-engine`
**Package** : `com.performance.platform.engine.correlation`, `com.performance.platform.engine.remote`
**Statut** : WAITING
**Specs de référence** : `improvements.md` B2, C2 ; `ADR-011-multi-claim-all-complete.md`
**Dépend de** : PDR-044 (completion order + onComplete), PDR-050 (RemoteStepDispatcher consolidé)
**Issues** : ISSUE-189, ISSUE-190

---

## Responsabilité

Rendre la complétion distribuée **déterministe** et **sans polling** :
1. **B2** : la sélection du résultat sous multi-claim (`results.values().stream()
   .filter(isSuccess).findFirst()`) dépend de l'ordre d'un `ConcurrentHashMap` → non
   déterministe. Pour une plateforme dont la sortie EST une mesure, c'est un bug.
2. **C2** : le polling 500 ms (`POLL_COMPLETION_MS` déclaré deux fois) ajoute
   jusqu'à 500 ms × profondeur DAG sur le chemin critique.

> ADR requise (ADR-034) : politique de sélection de résultat documentée.

---

## Interfaces Publiques

```java
// platform-execution-engine
package com.performance.platform.engine.correlation;

public enum ResultSelectionPolicy {
    FIRST_COMPLETED,   // défaut — ordre de complétion enregistré par le tracker (déterministe)
    LAST_COMPLETED
}
```

Ajout au tracker (additif) :
```java
/** Sélectionne le résultat selon la politique, en se basant sur resultsInCompletionOrder. */
Optional<TaskResult> selectResult(MessageId messageId, ResultSelectionPolicy policy);
```

Config (`ExecutionEngineProperties`) : champ `execution.resultSelectionPolicy`
(défaut `FIRST_COMPLETED`).

`RemoteStepDispatcher.awaitCompletion` :
- enregistre `tracker.onComplete(messageId, policy, callback)` qui complète un
  `CompletableFuture<TaskResult>` ;
- `dispatch()` attend ce future (Virtual Thread) au lieu de `Thread.sleep(500)`;
- à réception, `tracker.selectResult(...)` puis `tracker.remove(messageId)` (éviction).

---

## Règles de Comportement

- `FIRST_COMPLETED` = premier résultat **dans l'ordre de complétion** (séquence
  atomique affectée dans `onCompleted`), jamais l'ordre d'itération d'une HashMap.
- `ResultSelectionDeterminismTest` (T6) : deux agents → deux résultats différents,
  la même politique sélectionne le même agent sur 1 000 runs.
- Le callback de complétion remplace intégralement le polling ; plus de
  `POLL_COMPLETION_MS` dans `RemoteStepDispatcher`.

---

## Dépendances Techniques

```
Ce PDR utilise :
  PDR-044 (resultsInCompletionOrder + onComplete), PDR-050 (RemoteStepDispatcher)

Ce PDR est utilisé par :
  (rien)
```

---

## Critères de Done (PDR complet)

- [ ] `ResultSelectionPolicy` + `selectResult` déterministe (ISSUE-189)
- [ ] `ResultSelectionDeterminismTest` (T6) passe 1 000/1 000 (ISSUE-189)
- [ ] Polling 500 ms remplacé par signal de complétion (ISSUE-190)
- [ ] `mvn test -pl platform-execution-engine -q` → 0 erreur
