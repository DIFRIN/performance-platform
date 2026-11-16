# ISSUE-208 — Traducteur Gatling du plan (modèle ouvert)

**PDR** : PDR-056
**Module** : `platform-injection-gatling`
**Statut** : WAITING
**Priorité** : P1
**Bloquée par** : ISSUE-206, ISSUE-201
**Estime** : L (3-6h)

---

## Objectif

`GatlingTaskExecutor` lit `parameters.workload`, le transmet au worker ; le worker le traduit en étapes Gatling ouvertes.

> Référence : ADR-064.

## Fichiers à Créer / Modifier

```
infra/platform-injection-gatling/src/main/java/com/performance/platform/injection/gatling/load/
  ├── WorkloadPlanTranslator.java   — interface (création)
  └── DefaultWorkloadPlanTranslator.java — toOpenSteps (création)
infra/platform-injection-gatling/src/main/java/com/performance/platform/injection/gatling/
  └── GatlingTaskExecutor.java      — lit le plan, publie workloadPlan + workloadPlanHash (modification)
infra/platform-injection-gatling/src/main/java/com/performance/platform/injection/gatling/runner/
  └── GatlingRunConfig.java, GatlingRunner.java, DefaultGatlingRunner.java — WorkloadPlan au lieu de LoadModel (modification)
infra/platform-injection-gatling/src/main/java/com/performance/platform/injection/gatling/worker/RunDirectory.java — plan dans le run directory (modification)
infra/platform-injection-gatling/src/test/java/com/performance/platform/injection/gatling/load/
  └── DefaultWorkloadPlanTranslatorTest.java
```

## Interfaces à Implémenter

> Signatures dans `PDR-056` section « Interfaces Publiques » — les appliquer telles quelles.

## Règles Spécifiques

- Correspondance : Ramp→`rampUsersPerSec`, Hold→`constantUsersPerSec`, Burst→`atOnceUsers`, Pause→`nothingFor`.
- `Ramp` avec `from == to` traduit en palier constant.
- `toClosedSteps` lève `UnsupportedOperationException` jusqu'à l'Issue suivante.
- Supprimer `LoadModelTranslator` et `DefaultLoadModelTranslator` ainsi que leurs tests.

## Critères de Done

- [ ] `mvn test -pl :platform-injection-gatling -q` → 0 erreur
- [ ] Plus aucune référence à `LoadModel` dans le module (grep)
- [ ] Sorties `workloadPlan` et `workloadPlanHash` présentes dans le `TaskResult`
- [ ] `.claude/workspace/progress.md` mis à jour : ISSUE-208 → DONE
- [ ] `.claude/workspace/interfaces-registry.md` mis à jour si interfaces concernées
