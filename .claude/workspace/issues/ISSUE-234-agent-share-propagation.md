# ISSUE-234 — Agent : propagation du share à l'executor

**PDR** : PDR-060
**Module** : `platform-agent-runtime`
**Statut** : APPROVED
**Priorité** : P1
**Bloquée par** : ISSUE-227, ISSUE-231
**Estime** : S (< 1h)

---

## Objectif

L'agent place le share reçu dans l'`ExecutionContext` passé à l'executor.

> Référence : ADR-067.

## Fichiers à Créer / Modifier

```
execution/platform-agent-runtime/src/main/java/com/performance/platform/agent/runtime/TaskAgentRuntime.java — `context.withShare(signal.share())` avant execute (modification)
(+ test)
```

## Interfaces à Implémenter

> Signatures dans `PDR-060` section « Interfaces Publiques » — les appliquer telles quelles.

## Règles Spécifiques

- `TaskExecutor.execute` garde sa signature.
- Log : `action=task_granted ... shareIndex={} shareCount={}`.

## Critères de Done

- [ ] `mvn test -pl :platform-agent-runtime -q` → 0 erreur
- [ ] L'executor de test lit le share reçu
- [ ] `.claude/workspace/progress.md` mis à jour : ISSUE-234 → DONE
- [ ] `.claude/workspace/interfaces-registry.md` mis à jour si interfaces concernées
