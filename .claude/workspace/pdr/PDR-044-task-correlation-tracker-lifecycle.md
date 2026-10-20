# PDR-044 — Task Correlation Tracker Lifecycle (eviction + perte d'agent + signal)

**Module Maven** : `platform-execution-engine`
**Package** : `com.performance.platform.engine.correlation`
**Statut** : WAITING
**Specs de référence** : `improvements.md` C3, B3 ; `ADR-011-multi-claim-all-complete.md`
**Dépend de** : PDR-006 (tracker déjà DONE)
**Issues** : ISSUE-171

---

## Responsabilité

Combler les trois déficits du `TaskCorrelationTracker` :
1. **Éviction (C3)** : `states` est un `ConcurrentHashMap` sans `remove` ni `clear`
   → fuite mémoire monotone de l'orchestrateur.
2. **Perte d'agent (B3)** : aucun moyen de marquer comme échouées les claims d'un
   agent perdu, donc `ALL_COMPLETE` est insatisfiable (stall 300 s).
3. **Signal de complétion (C2)** : le dispatcher poll à 500 ms au lieu d'être
   notifié. Le tracker doit exposer un callback de complétion.

Ce PDR rend le tracker auto-nettoyant, notifiable, et capable de clôturer les
claims orphelins. Additif uniquement — aucune signature existante supprimée.

---

## Interfaces Publiques

> Ajouts à l'interface existante (les méthodes actuelles restent inchangées).

```java
package com.performance.platform.engine.correlation;

public interface TaskCorrelationTracker {

    // === existantes (inchangées) ===
    void trackDispatched(MessageId messageId, TaskId taskId, ExecutionId executionId);
    void onClaimed(MessageId messageId, AgentId agentId);
    void onCompleted(MessageId messageId, AgentId agentId, TaskResult result);
    void onFailed(MessageId messageId, AgentId agentId, String error);
    Set<AgentId> claimsFor(MessageId messageId);
    boolean isComplete(MessageId messageId, TaskCompletionPolicy policy);
    Map<AgentId, TaskResult> getResults(MessageId messageId);
    TaskId taskIdFor(MessageId messageId);

    // === NOUVELLES ===

    /**
     * Marque comme échouées toutes les claims en attente de {@code agentId}
     * (agent perdu / TTL expiré). Rend ALL_COMPLETE satisfiable et déclenche
     * le callback de complétion enregistré via {@link #onComplete}.
     */
    void onAgentLost(AgentId agentId);

    /**
     * Évacue l'état de corrélation d'un messageId terminé (après complétion
     * consommée par le dispatcher). Idempotent.
     */
    void remove(MessageId messageId);

    /** Nombre de corrélations actives (observabilité + test mémoire T5). */
    int size();

    /**
     * Enregistre un callback one-shot invoqué (sur le thread appelant de
     * onCompleted/onFailed/onAgentLost) la première fois que
     * {@code isComplete(messageId, policy)} devient true — ou immédiatement
     * si déjà complet. Remplace le polling 500 ms (C2).
     */
    void onComplete(MessageId messageId, TaskCompletionPolicy policy, Runnable callback);

    /**
     * Résultats dans l'ordre de complétion (croissant). Déterministe —
     * utilisé par PDR-051 pour la sélection multi-claim (B2).
     */
    List<TaskResult> resultsInCompletionOrder(MessageId messageId);
}
```

`DefaultTaskCorrelationTracker.CorrelationState` gagne :
- `AtomicLong completionCounter` (séquence monotone affectée à chaque onCompleted/onFailed)
- `ConcurrentSkipListMap<Long, AgentId> completionOrder` (séquence → agentId)
- un registre de callbacks `onComplete` évalués à chaque mutation

---

## Règles de Comportement

- `onAgentLost` doit : pour chaque messageId où `claimedAgents` contient l'agent
  et où l'agent n'a ni complété ni échoué → ajouter à `failedAgents` + publier un
  pseudo-échec (« agent lost ») afin que `isAllComplete` soit satisfait.
- `remove(messageId)` est appelé par le dispatcher APRÈS consommation du résultat ;
  jamais pendant que des callbacks de complétion peuvent encore s'exécuter.
- `onComplete` : un seul déclenchement par messageId par policy (thread-safe).

---

## Dépendances Techniques

```
Ce PDR utilise :
  PDR-006 (TaskCorrelationTracker, CorrelationState)

Ce PDR est utilisé par :
  PDR-045 (fast-fail via onAgentLost)
  PDR-051 (sélection + signal via resultsInCompletionOrder / onComplete)
```

---

## Critères de Done (PDR complet)

- [ ] `DefaultTaskCorrelationTracker` implémente les 5 nouvelles méthodes
- [ ] `CorrelationTrackerMemoryTest` (T5) : 10 000 dispatches → `size()` revient à 0 après complétion + éviction
- [ ] `onAgentLost` rend `isComplete(ALL_COMPLETE)` true pour un claim orphelin
- [ ] `mvn test -pl platform-execution-engine -q` → 0 erreur
