# PDR-061 — Weighted Request Mix

**Module Maven** : `platform-injection-gatling` (+ platform-scenario-dsl, platform-assertion)
**Package** : `com.performance.platform.injection.gatling.http`
**Statut** : WAITING
**Specs de référence** : `docs/knowledge/adr/` ADR-070
**Dépend de** : PDR-056, PDR-058
**Issues** : ISSUE-238, ISSUE-239, ISSUE-240, ISSUE-241, ISSUE-242, ISSUE-243

---

## Responsabilité

Une étape d'injection peut déclarer plusieurs requêtes pondérées ; chaque utilisateur virtuel en envoie
une seule, tirée au sort selon les poids. Les résultats sont disponibles par nom de requête et au total.

Ne fait PAS : enchaînement, branchement, extraction, corrélation, pauses, boucles — les parcours
multi-requêtes restent des simulations Java custom (ADR-065).

---

## Interfaces Publiques

> Signatures exactes. Ce qui est écrit ici prime sur l'ADR en cas de différence.

```java
package com.performance.platform.injection.gatling.http;
public record WeightedRequest(String name, double weight, HttpRequestSpec spec) {}
public record RequestMix(List<WeightedRequest> requests) {
    public static RequestMix single(HttpRequestSpec spec);     // forme actuelle = mix d'une requête "default"
    public double normalizedWeight(String name);
}

// Résultats par requête : InjectionResult.perRequest : Map<String, RequestStats>
package com.performance.platform.domain.injection;
public record RequestStats(long requests, long failures, LatencyHistogram histogram) {}
```

---

## Règles de Comportement

- Poids strictement positifs, normalisés (somme quelconque). Noms uniques, `[a-z0-9-]+`.
- Tirage déterministe : graine = hash du plan + index du share.
- Une arrivée = une requête (propriété requise par la comptabilité planifié/réalisé).
- Métriques nommées : `injection.latency.p95[create]` ; sans suffixe = total.
- Le rapport affiche le mix réalisé à côté des poids déclarés.

---

## Issues

| ID | Titre | Module | Priorité | Taille | Bloquée par |
|---|---|---|---|---|---|
| ISSUE-238 | RequestStats et résultats par requête | `platform-domain` | P2 | S | ISSUE-215 |
| ISSUE-239 | Validation du bloc requests | `platform-scenario-dsl` | P2 | S | ISSUE-207 |
| ISSUE-240 | Mix pondéré HTTP | `platform-injection-gatling` | P2 | M | ISSUE-208 |
| ISSUE-241 | Mix pondéré Kafka | `platform-injection-gatling` | P2 | M | ISSUE-240 |
| ISSUE-242 | Résultats par nom de requête | `platform-injection-gatling` | P2 | M | ISSUE-240, ISSUE-218, ISSUE-238 |
| ISSUE-243 | Métriques d'assertion par requête | `platform-assertion` | P2 | M | ISSUE-242, ISSUE-219 |

---

## Dépendances Techniques

```
Ce PDR utilise     : PDR-056, PDR-058
Ce PDR est utilisé par : PDR-062
```

---

## Critères de Done (PDR complet)

- [ ] Toutes les Issues du PDR sont DONE
- [ ] Les interfaces sont dans `.claude/workspace/interfaces-registry.md` avec statut STABLE
- [ ] Les tests d'intégration (si applicable) passent
