# ISSUE-021 — TaskCorrelationTracker (multi-claim 1:N)

**PDR** : PDR-006
**Module** : `platform-execution-engine`
**Statut** : WAITING
**Priorité** : P1
**Bloquée par** : ISSUE-019
**Estime** : M

---

## Objectif

Implémenter `TaskCorrelationTracker` qui suit la corrélation `MessageId → claims → résultats`
(1:N) et détermine la complétion selon `TaskCompletionPolicy`.

## Fichiers à Créer

```
platform-execution-engine/src/main/java/com/performance/platform/engine/correlation/
  ├── TaskCorrelationTracker.java          — interface
  └── DefaultTaskCorrelationTracker.java   — implémentation thread-safe

platform-execution-engine/src/test/java/com/performance/platform/engine/correlation/
  └── DefaultTaskCorrelationTrackerTest.java — FIRST_COMPLETE vs ALL_COMPLETE
```

## Interfaces à Implémenter

```java
public interface TaskCorrelationTracker {
    void trackDispatched(MessageId messageId, TaskId taskId, ExecutionId executionId);
    void onClaimed(MessageId messageId, AgentId agentId);
    void onCompleted(MessageId messageId, AgentId agentId, TaskResult result);
    void onFailed(MessageId messageId, AgentId agentId, String error);
    Set<AgentId> claimsFor(MessageId messageId);
    boolean isComplete(MessageId messageId, TaskCompletionPolicy policy);
}

@Component
public class DefaultTaskCorrelationTracker implements TaskCorrelationTracker { /* ConcurrentHashMap */ }
```

## Règles Spécifiques

- `FIRST_COMPLETE` : complet dès le 1er `onCompleted`.
- `ALL_COMPLETE` : complet quand tous les agents ayant claimé ont complété OU échoué.
- Thread-safe (plusieurs agents publient en parallèle).
- `claimsFor` retourne l'ensemble des agents ayant claimé pour ce messageId.

## Critères de Done

- [ ] `mvn test -pl platform-execution-engine -q` → 0 erreur
- [ ] 2 claims, 1 completed, policy FIRST_COMPLETE → `isComplete` true
- [ ] 2 claims, 1 completed, policy ALL_COMPLETE → `isComplete` false ; après 2e → true
- [ ] `.claude/progress.md` mis à jour : ISSUE-021 → DONE
- [ ] `.claude/context/interfaces-registry.md` : `TaskCorrelationTracker` → STABLE
