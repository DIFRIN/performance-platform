# ISSUE-216 — Reduction.MERGE et catalogue de métriques

**PDR** : PDR-058
**Module** : `platform-domain`
**Statut** : DONE
**Priorité** : P0
**Bloquée par** : ISSUE-215
**Estime** : M (1-3h)

---

## Objectif

Ajouter `Reduction.MERGE` et mettre à jour `MetricCatalog` selon ADR-068 §3.

> Référence : ADR-068.

## Fichiers à Créer / Modifier

```
core/platform-domain/src/main/java/com/performance/platform/domain/assertion/
  ├── Reduction.java                — + MERGE (modification)
  └── MetricCatalog.java            — métriques modifiées et nouvelles (modification)
core/platform-domain/src/test/java/com/performance/platform/domain/assertion/MetricCatalogTest.java
```

## Interfaces à Implémenter

> Signatures dans `PDR-058` section « Interfaces Publiques » — les appliquer telles quelles.

## Règles Spécifiques

- `injection.latency.p50/p75/p90/p95/p99` → `MERGE`. Ajouter `...pNN.worst-agent` → `MAX`.
- Ajouter : `injection.arrival.planned` (SUM), `.achieved` (SUM), `.deviation` (percent), `injection.start.skew` (ms), `injection.generator.cpu` (MAX, percent), `injection.generator.gc.pause` (MAX, ms).
- `injection.throughput` : réduction documentée « fenêtre commune » (calcul dans `platform-assertion`).
- Les `switch` exhaustifs sur `Reduction` dans d'autres modules : ajouter une branche `default` n'est PAS dans cette Issue — vérifier la compilation globale et signaler.

## Critères de Done

- [ ] `mvn test -pl :platform-domain -q` → 0 erreur
- [ ] Catalogue conforme au tableau ADR-068 §3
- [ ] Build complet compile
- [ ] `.claude/workspace/progress.md` mis à jour : ISSUE-216 → DONE
- [ ] `.claude/workspace/interfaces-registry.md` mis à jour si interfaces concernées
