# ISSUE-190 — Poll → Completion Signal (C2)

**PDR** : PDR-051
**Module** : `platform-execution-engine`
**Statut** : WAITING
**Priorité** : P2
**Bloquée par** : ISSUE-171, ISSUE-189
**Estime** : M (1-3h)

---

## Objectif

Supprimer le polling 500 ms sur le chemin critique DAG (C2) : `RemoteStepDispatcher`
attend un `CompletableFuture` complété par le callback `tracker.onComplete(...)`,
puis consomme le résultat et appelle `tracker.remove(...)` (éviction).

## Fichiers à Modifier

```
platform-execution-engine/src/main/java/com/performance/platform/engine/remote/
  └── RemoteStepDispatcher.java        — remplacer awaitCompletion (polling) par signal + remove
```

## Règles Spécifiques

- Plus de `POLL_COMPLETION_MS` ni `Thread.sleep(500)` dans `RemoteStepDispatcher`.
- L'attente est un `CompletableFuture<TaskResult>` attendu sur Virtual Thread.
- Après consommation : `tracker.remove(messageId)` pour libérer la mémoire.

## Critères de Done

- [ ] `RemoteStepDispatcher` sans polling
- [ ] `mvn test -pl platform-execution-engine -q` → 0 erreur
- [ ] `.claude/workspace/progress.md` mis à jour : ISSUE-190 → DONE
