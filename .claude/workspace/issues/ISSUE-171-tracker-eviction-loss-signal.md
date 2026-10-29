# ISSUE-171 — TaskCorrelationTracker Eviction + Loss + Completion Signal

**PDR** : PDR-044
**Module** : `platform-execution-engine`
**Statut** : DONE
**Priorité** : P0 (prévient l'OOM production, débloque B3/B4 et C2)
**Bloquée par** : —
**Estime** : L (3-6h)

---

## Objectif

Implémenter les 5 méthodes ajoutées au `TaskCorrelationTracker` (éviction, perte
d'agent, signal de complétion, ordre de complétion, size) et le test mémoire T5.

## Fichiers à Modifier

```
execution/platform-execution-engine/src/main/java/com/performance/platform/engine/correlation/
  ├── TaskCorrelationTracker.java          — ajouter onAgentLost/remove/size/onComplete/resultsInCompletionOrder
  └── DefaultTaskCorrelationTracker.java   — implémenter + enrichir CorrelationState

execution/platform-execution-engine/src/test/java/com/performance/platform/engine/correlation/
  └── CorrelationTrackerMemoryTest.java    — NOUVEAU (T5)
```

## Interfaces à Implémenter

```java
void onAgentLost(AgentId agentId);
void remove(MessageId messageId);
int size();
void onComplete(MessageId messageId, TaskCompletionPolicy policy, Runnable callback);
List<TaskResult> resultsInCompletionOrder(MessageId messageId);
```

## Règles Spécifiques

- `CorrelationState` gagne `AtomicLong completionCounter` + `ConcurrentSkipListMap<Long,AgentId> completionOrder`.
- `onAgentLost` : marque en `failedAgents` les claims en attente de l'agent (rend ALL_COMPLETE satisfiable).
- `onComplete` : un seul déclenchement par messageId par policy (thread-safe).
- T5 : 10 000 dispatches → après complétion + `remove`, `size()==0` et référence bornée.

## Critères de Done

- [ ] `mvn test -pl :platform-execution-engine -q` → 0 erreur
- [ ] `CorrelationTrackerMemoryTest` (T5) vert
- [ ] `onAgentLost` rend `isComplete(ALL_COMPLETE)` vrai pour un claim orphelin
- [ ] `.claude/workspace/progress.md` mis à jour : ISSUE-171 → DONE
