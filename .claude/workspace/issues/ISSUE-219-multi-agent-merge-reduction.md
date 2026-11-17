# ISSUE-219 — Réduction multi-agents : MERGE et fenêtre commune

**PDR** : PDR-058
**Module** : `platform-assertion`
**Statut** : APPROVED
**Priorité** : P1
**Bloquée par** : ISSUE-216, ISSUE-218
**Estime** : L (3-6h)

---

## Objectif

Le pipeline d'assertion calcule les métriques d'injection multi-agents : percentiles par fusion, débit sur fenêtre commune, arrivées, skew, santé.

> Référence : ADR-068.

## Fichiers à Créer / Modifier

```
performance/platform-assertion/src/main/java/com/performance/platform/assertion/
  ├── InjectionAggregator.java      — agrège les contributions par agent (création)
  └── (point de réduction existant) — branche MERGE (modification)
performance/platform-assertion/src/test/java/com/performance/platform/assertion/
  └── InjectionAggregatorTest.java  — 1 agent, 3 agents, départs décalés, agent sans histogramme
```

## Interfaces à Implémenter

> Signatures dans `PDR-058` section « Interfaces Publiques » — les appliquer telles quelles.

## Règles Spécifiques

- Localiser le point de réduction actuel à partir des usages de `Reduction` ; ne pas créer un second pipeline.
- Fenêtre commune = fenêtres mesurées couvertes par tous les agents contributeurs.
- `deviation = abs(achieved - planned) / planned x 100` ; `planned == 0` ou inconnu => `INCONCLUSIVE`.
- Contribution sans histogramme (ancien résultat) : percentile global `INCONCLUSIVE`, worst-agent calculé.
- Un seul agent : résultats identiques à aujourd'hui.

## Critères de Done

- [x] `mvn test -pl :platform-assertion -q` → 0 erreur
- [x] p95 global de 3 agents = p95 de l'histogramme fusionné
- [x] Débit = somme des requêtes / durée de la fenêtre commune
- [ ] `.claude/workspace/progress.md` mis à jour : ISSUE-219 → DONE
- [ ] `.claude/workspace/interfaces-registry.md` mis à jour si interfaces concernées
