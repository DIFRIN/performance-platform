# PDR-058 — Mergeable Injection Measurements

**Module Maven** : `platform-domain` (+ platform-injection-gatling, platform-assertion, platform-reporting)
**Package** : `com.performance.platform.domain.injection`
**Statut** : WAITING
**Specs de référence** : `docs/knowledge/adr/` ADR-068
**Dépend de** : PDR-056, PDR-055
**Issues** : ISSUE-215, ISSUE-216, ISSUE-217, ISSUE-218, ISSUE-219, ISSUE-220

---

## Responsabilité

Chaque agent renvoie la distribution des latences et des compteurs par fenêtre de temps, avec les
arrivées planifiées et réalisées. L'agrégation multi-agents fusionne les histogrammes (percentile global
exact), somme les compteurs sur une fenêtre commune et ignore le warm-up.

Ne fait PAS : flux de métriques en direct (ADR-050), délai d'ordonnancement par arrivée (non exposé par Gatling).

---

## Interfaces Publiques

> Signatures exactes. Ce qui est écrit ici prime sur l'ADR en cas de différence.

```java
package com.performance.platform.domain.injection;

public record LatencyHistogram(Map<Long, Long> okBuckets, Map<Long, Long> koBuckets) {
    public static long bucketOf(long latencyMs);      // 1 ms jusqu'à 1000 ms, puis 2 chiffres significatifs
    public LatencyHistogram merge(LatencyHistogram other);
    public long quantile(double q);                    // q in ]0,1], sur ok+ko ; IllegalStateException si vide
    public long count();
    public static LatencyHistogram empty();
}

public record LoadWindow(Instant start, Duration length, long plannedArrivals, long startedUsers,
                         long requests, long failures, boolean measured) {}

public record GeneratorHealth(double cpuLoad, Duration gcPause, long peakHeapBytes) {}

// InjectionResult gagne (constructeur de compatibilité conservé) :
//   Instant startedAt, Instant endedAt, LatencyHistogram histogram,
//   List<LoadWindow> windows, GeneratorHealth generatorHealth, boolean complete

package com.performance.platform.domain.assertion;
public enum Reduction { SUM, MAX, MIN, WEIGHTED, ALL, ANY, FIRST, MERGE }
```

---

## Règles de Comportement

- Fenêtres alignées sur l'horloge murale ; taille `injection.window` (défaut 5s).
- Catalogue : `injection.latency.pNN` -> MERGE ; nouveau `injection.latency.pNN.worst-agent` -> MAX ; `injection.throughput` = somme des requêtes / fenêtre commune mesurée.
- Nouvelles métriques : `injection.arrival.planned`, `.achieved`, `.deviation` (%), `injection.start.skew` (ms), `injection.generator.cpu`, `injection.generator.gc.pause`.
- Seules les fenêtres `measured` entrent dans les métriques. Fenêtres partielles de début/fin exclues du débit.
- Aucune nouvelle dépendance Maven (pas de HdrHistogram).
- Simulation opaque : métriques d'arrivée `INCONCLUSIVE`.
- Résultats stockés sans histogramme : percentiles globaux « indisponibles », jamais recalculés.

---

## Issues

| ID | Titre | Module | Priorité | Taille | Bloquée par |
|---|---|---|---|---|---|
| ISSUE-215 | Histogramme, fenêtres et santé dans InjectionResult | `platform-domain` | P0 | M | ISSUE-204 |
| ISSUE-216 | Reduction.MERGE et catalogue de métriques | `platform-domain` | P0 | M | ISSUE-215 |
| ISSUE-217 | Parser : histogramme et fenêtres | `platform-injection-gatling` | P1 | L | ISSUE-215, ISSUE-208 |
| ISSUE-218 | Santé du générateur et sorties du résultat | `platform-injection-gatling` | P1 | M | ISSUE-217, ISSUE-202 |
| ISSUE-219 | Réduction multi-agents : MERGE et fenêtre commune | `platform-assertion` | P1 | L | ISSUE-216, ISSUE-218 |
| ISSUE-220 | Rapport : charge planifiée, réalisée et distributions | `platform-reporting` | P2 | L | ISSUE-219 |

---

## Dépendances Techniques

```
Ce PDR utilise     : PDR-056, PDR-055
Ce PDR est utilisé par : PDR-059, PDR-061, PDR-062
```

---

## Critères de Done (PDR complet)

- [ ] Toutes les Issues du PDR sont DONE
- [ ] Les interfaces sont dans `.claude/workspace/interfaces-registry.md` avec statut STABLE
- [ ] Les tests d'intégration (si applicable) passent
