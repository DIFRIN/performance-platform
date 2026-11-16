# ISSUE-207 — Validation du plan au parsing

**PDR** : PDR-056
**Module** : `platform-scenario-dsl`
**Statut** : WAITING
**Priorité** : P0
**Bloquée par** : ISSUE-204
**Estime** : M (1-3h)

---

## Objectif

`DefaultScenarioValidator` valide le plan de chaque step d'injection avant tout dispatch.

> Référence : ADR-064.

## Fichiers à Créer / Modifier

```
performance/platform-scenario-dsl/src/main/java/com/performance/platform/scenario/validation/
  ├── DefaultScenarioValidator.java — appelle le validateur de plan (modification)
  └── WorkloadPlanValidator.java    — règles (création)
performance/platform-scenario-dsl/src/test/java/com/performance/platform/scenario/validation/
  └── WorkloadPlanValidatorTest.java
```

## Interfaces à Implémenter

> Signatures dans `PDR-056` section « Interfaces Publiques » — les appliquer telles quelles.

## Règles Spécifiques

- Rejets : plan vide, `Burst` en modèle `CLOSED`, aucune étape `measured`, type d'étape inconnu, clé `unit` (dont `requests_per_second`).
- Remplacer la validation actuelle de `LoadModel` dans ce validateur.
- Une erreur par règle, message avec l'id du step.

## Critères de Done

- [ ] `mvn test -pl :platform-scenario-dsl -q` → 0 erreur
- [ ] Chaque règle a un test positif et négatif
- [ ] `.claude/workspace/progress.md` mis à jour : ISSUE-207 → DONE
- [ ] `.claude/workspace/interfaces-registry.md` mis à jour si interfaces concernées
