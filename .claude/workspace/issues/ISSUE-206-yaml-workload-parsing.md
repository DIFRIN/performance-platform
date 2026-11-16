# ISSUE-206 — Parsing YAML de workload et presets

**PDR** : PDR-056
**Module** : `platform-scenario-yaml`
**Statut** : WAITING
**Priorité** : P0
**Bloquée par** : ISSUE-205
**Estime** : M (1-3h)

---

## Objectif

Le parseur normalise `parameters.workload` et développe `parameters.loadModel` en `workload` canonique.

> Référence : ADR-064.

## Fichiers à Créer / Modifier

```
infra/platform-scenario-yaml/src/main/java/com/performance/platform/scenario/parser/
  └── YamlScenarioParser.java       — normalisation des paramètres d'injection (modification)
infra/platform-scenario-yaml/src/test/java/com/performance/platform/scenario/parser/
  └── WorkloadParsingTest.java      — workload, preset, les deux, invalide
```

## Interfaces à Implémenter

> Signatures dans `PDR-056` section « Interfaces Publiques » — les appliquer telles quelles.

## Règles Spécifiques

- Après parsing : `parameters.workload` = `WorkloadPlanCodec.toMap(plan)` ; `loadModel` retiré des paramètres.
- `workload` et `loadModel` présents ensemble => erreur de parsing.
- Erreurs du codec/presets converties en erreur de scénario (HTTP 400), avec l'id du step.

## Critères de Done

- [ ] `mvn test -pl :platform-scenario-yaml -q` → 0 erreur
- [ ] Les scénarios de `distribution/example/scenarios` se parsent sans changement
- [ ] Erreur claire si les deux clés sont présentes
- [ ] `.claude/workspace/progress.md` mis à jour : ISSUE-206 → DONE
- [ ] `.claude/workspace/interfaces-registry.md` mis à jour si interfaces concernées
