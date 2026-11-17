# ISSUE-233 — Moteur : politique de perte d'agent

**PDR** : PDR-060
**Module** : `platform-execution-engine`
**Statut** : DONE
**Priorité** : P2
**Bloquée par** : ISSUE-232, ISSUE-202
**Estime** : M (1-3h)

---

## Objectif

Appliquer `onAgentLoss` quand un agent granté est perdu pendant une étape `fanOut: all`.

> Référence : ADR-067.

## Fichiers à Créer / Modifier

```
execution/platform-execution-engine/src/main/java/com/performance/platform/engine/remote/TransportStepDispatcher.java (ou composant de complétion associé) (modification)
(+ tests : abort, continue)
```

## Interfaces à Implémenter

> Signatures dans `PDR-060` section « Interfaces Publiques » — les appliquer telles quelles.

## Règles Spécifiques

- `ABORT` : étape FAILED immédiatement ; annulation envoyée aux agents restants par le mécanisme de cleanup existant.
- `CONTINUE` : l'étape attend les agents restants ; le résultat enregistre `grantedAgents` et `reportingAgents`.
- Détection par l'événement `AgentLost` existant ; pas de nouveau mécanisme de heartbeat.

## Critères de Done

- [ ] `mvn test -pl :platform-execution-engine -q` → 0 erreur
- [ ] Perte d'un agent sur trois : ABORT => FAILED ; CONTINUE => 2 résultats, compteurs corrects
- [ ] `.claude/workspace/progress.md` mis à jour : ISSUE-233 → DONE
- [ ] `.claude/workspace/interfaces-registry.md` mis à jour si interfaces concernées
