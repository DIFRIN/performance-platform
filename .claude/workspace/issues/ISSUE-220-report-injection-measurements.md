# ISSUE-220 — Rapport : charge planifiée, réalisée et distributions

**PDR** : PDR-058
**Module** : `platform-reporting`
**Statut** : DONE
**Priorité** : P2
**Bloquée par** : ISSUE-219, ISSUE-246
**Estime** : L (3-6h)

---

## Objectif

La section injection du rapport présente courbes planifié/réalisé, percentiles globaux et worst-agent, contributions par agent, santé du générateur ; histogrammes et fenêtres sont conservés comme preuves.

> Référence : ADR-068.

## Fichiers à Créer / Modifier

```
performance/platform-reporting/src/main/java/com/performance/platform/reporting/engine/
  └── (builder de la section injection) (modification)
performance/platform-reporting/src/test/java/com/performance/platform/reporting/engine/
  └── InjectionMeasurementsReportTest.java
```

## Interfaces à Implémenter

> Signatures dans `PDR-058` section « Interfaces Publiques » — les appliquer telles quelles.

## Règles Spécifiques

- Preuves (ADR-051) : histogramme fusionné + fenêtres agrégées stockés avec le rapport.
- Étapes de warm-up visibles dans la chronologie, marquées « non mesuré ».
- Changement requis dans `platform-report-api` ou `platform-report-renderers` : BLOQUER et demander une Issue dédiée.

## Critères de Done

- [ ] `mvn test -pl :platform-reporting -q` → 0 erreur
- [ ] Le modèle de rapport expose planifié, réalisé, global, worst-agent, par agent, santé
- [ ] `.claude/workspace/progress.md` mis à jour : ISSUE-220 → DONE
- [ ] `.claude/workspace/interfaces-registry.md` mis à jour si interfaces concernées
