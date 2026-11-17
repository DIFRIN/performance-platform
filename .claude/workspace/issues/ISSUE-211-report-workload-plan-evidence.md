# ISSUE-211 — Plan résolu et hash dans le rapport

**PDR** : PDR-056
**Module** : `platform-reporting`
**Statut** : APPROVED
**Priorité** : P2
**Bloquée par** : ISSUE-208, ISSUE-245
**Estime** : M (1-3h)

---

## Objectif

Le rapport affiche, pour chaque étape d'injection, le plan résolu (étapes, modèle, scope) et son hash.

> Référence : ADR-064.

## Fichiers à Créer / Modifier

```
performance/platform-reporting/src/main/java/com/performance/platform/reporting/engine/
  └── (builder de la section injection) — lire `workloadPlan` / `workloadPlanHash` (modification)
performance/platform-reporting/src/test/java/com/performance/platform/reporting/engine/
  └── WorkloadPlanEvidenceTest.java
```

## Interfaces à Implémenter

> Signatures dans `PDR-056` section « Interfaces Publiques » — les appliquer telles quelles.

## Règles Spécifiques

- Données lues depuis les sorties du `TaskResult` ; absentes (ancien run) => « plan non enregistré ».
- Les champs `workloadPlan` / `workloadPlanHash` de `InjectionReportEntry` sont fournis par ISSUE-245 : les renseigner dans `DefaultReportEngine.buildInjectionEntries` à partir des sorties `workloadPlan` / `workloadPlanHash` du `TaskResult`.

## Critères de Done

- [ ] `mvn test -pl :platform-reporting -q` → 0 erreur
- [ ] Le plan et le hash apparaissent dans le modèle de rapport et la sortie JSON
- [ ] `.claude/workspace/progress.md` mis à jour : ISSUE-211 → DONE
- [ ] `.claude/workspace/interfaces-registry.md` mis à jour si interfaces concernées
