# ISSUE-198 — Refus de claim si executor indisponible

**PDR** : PDR-055
**Module** : `platform-agent-runtime`
**Statut** : DONE
**Priorité** : P0
**Bloquée par** : ISSUE-197
**Estime** : M (1-3h)

---

## Objectif

L'agent ne claim pas une tâche dont l'executor implémente `ExecutorAvailability` et répond `false`.

> Référence : ADR-066.

## Fichiers à Créer / Modifier

```
execution/platform-agent-runtime/src/main/java/com/performance/platform/agent/runtime/
  └── TaskAgentRuntime.java         — consulter la disponibilité avant le claim (modification)
execution/platform-agent-runtime/src/test/java/com/performance/platform/agent/runtime/
  └── TaskAgentRuntimeAvailabilityTest.java — claim émis / non émis
```

## Interfaces à Implémenter

> Signatures dans `PDR-055` section « Interfaces Publiques » — les appliquer telles quelles.

## Règles Spécifiques

- Aucun `if/switch` sur le nom de tâche : test `instanceof ExecutorAvailability` sur l'executor résolu par le registre.
- Log : `action=claim_skipped agentId={} messageId={} taskName={} reason=executor_unavailable`.
- Un executor sans cette interface garde le comportement actuel.

## Critères de Done

- [ ] `mvn test -pl :platform-agent-runtime -q` → 0 erreur
- [ ] Executor indisponible => aucun claim émis
- [ ] Executor disponible ou sans interface => claim émis comme avant
- [ ] `.claude/workspace/progress.md` mis à jour : ISSUE-198 → DONE
- [ ] `.claude/workspace/interfaces-registry.md` mis à jour si interfaces concernées
