# ISSUE-230 — Validation : scope global et minAgents

**PDR** : PDR-060
**Module** : `platform-scenario-dsl`
**Statut** : APPROVED
**Priorité** : P1
**Bloquée par** : ISSUE-228, ISSUE-207
**Estime** : S (< 1h)

---

## Objectif

Rejeter au parsing les combinaisons invalides.

> Référence : ADR-067.

## Fichiers à Créer / Modifier

```
performance/platform-scenario-dsl/src/main/java/com/performance/platform/scenario/validation/WorkloadPlanValidator.java   — règles de scope (modification)
(+ tests)
```

## Interfaces à Implémenter

> Signatures dans `PDR-060` section « Interfaces Publiques » — les appliquer telles quelles.

## Règles Spécifiques

- `scope: global` exige `fanOut: all`.
- `minAgents > 1` exige `fanOut: all`.
- `onAgentLoss` déclaré sur un step `fanOut: one` => erreur.

## Critères de Done

- [ ] `mvn test -pl :platform-scenario-dsl -q` → 0 erreur
- [ ] Trois règles testées
- [ ] `.claude/workspace/progress.md` mis à jour : ISSUE-230 → DONE
- [ ] `.claude/workspace/interfaces-registry.md` mis à jour si interfaces concernées
