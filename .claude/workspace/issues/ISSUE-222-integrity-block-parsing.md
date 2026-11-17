# ISSUE-222 — Bloc integrity : validation scénario

**PDR** : PDR-059
**Module** : `platform-scenario-dsl`
**Statut** : APPROVED
**Priorité** : P2
**Bloquée par** : ISSUE-221, ISSUE-207
**Estime** : S (< 1h)

---

## Objectif

Valider le bloc optionnel `integrity: { maxArrivalDeviation, maxGeneratorCpu }` d'un step d'injection.

> Référence : ADR-069.

## Fichiers à Créer / Modifier

```
performance/platform-scenario-dsl/src/main/java/com/performance/platform/scenario/validation/
  └── IntegrityBlockValidator.java  — (création) + appel depuis DefaultScenarioValidator
performance/platform-scenario-dsl/src/test/java/com/performance/platform/scenario/validation/IntegrityBlockValidatorTest.java
```

## Interfaces à Implémenter

> Signatures dans `PDR-059` section « Interfaces Publiques » — les appliquer telles quelles.

## Règles Spécifiques

- Formats acceptés : `5%` ou nombre. Hors ]0,100] => erreur de scénario.
- Clé inconnue dans `integrity` => erreur. Bloc autorisé uniquement en phase INJECTION.
- Le bloc reste dans `step.parameters` (lu par le rapport).

## Critères de Done

- [ ] `mvn test -pl :platform-scenario-dsl -q` → 0 erreur
- [ ] Valeurs invalides rejetées avec l'id du step
- [ ] `.claude/workspace/progress.md` mis à jour : ISSUE-222 → DONE
- [ ] `.claude/workspace/interfaces-registry.md` mis à jour si interfaces concernées
