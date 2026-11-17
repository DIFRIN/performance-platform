# PDR-056 — Composable Workload Plan

**Module Maven** : `platform-domain` (+ platform-scenario-yaml, platform-scenario-dsl, platform-injection-gatling, platform-reporting)
**Package** : `com.performance.platform.domain.injection`
**Statut** : WAITING
**Specs de référence** : `docs/knowledge/adr/` ADR-064
**Dépend de** : PDR-055
**Issues** : ISSUE-204, ISSUE-205, ISSUE-206, ISSUE-207, ISSUE-208, ISSUE-209, ISSUE-210, ISSUE-211, ISSUE-245

---

## Responsabilité

Le profil de charge devient un plan typé, immuable et indépendant de Gatling : modèle d'arrivée
(ouvert/fermé) + liste d'étapes (`ramp`, `hold`, `burst`, `pause`). Les huit types actuels deviennent des
presets YAML développés en plan avant validation. Le plan résolu et son hash SHA-256 sont conservés avec
l'exécution et affichés dans le rapport.

Ne fait PAS : registre racine de modèles (ADR-053), modèle « requêtes/s » (ADR-065), répartition entre agents (PDR Grant Share).

---

## Interfaces Publiques

> Signatures exactes. Ce qui est écrit ici prime sur l'ADR en cas de différence.

```java
package com.performance.platform.domain.injection;

public enum ArrivalModel { OPEN, CLOSED }          // OPEN : users/s — CLOSED : users concurrents
public enum LoadScope { PER_AGENT, GLOBAL }        // défaut PER_AGENT (sémantique : PDR Grant Share)

public sealed interface Stage permits Ramp, Hold, Burst, Pause {
    boolean measured();                             // false = warm-up, exclu des métriques
    Duration duration();                            // Burst -> Duration.ZERO
}
public record Ramp(double from, double to, Duration duration, boolean measured) implements Stage {}
public record Hold(double level, Duration duration, boolean measured) implements Stage {}
public record Burst(long users, boolean measured) implements Stage {}
public record Pause(Duration duration, boolean measured) implements Stage {}

public record WorkloadPlan(ArrivalModel model, LoadScope scope, List<Stage> stages) {
    public Duration injectionWindow();              // somme des durées
}

public final class WorkloadPlanCodec {              // Java pur, sans Jackson
    public static final String PARAM_KEY = "workload";
    public static WorkloadPlan fromMap(Map<String, Object> map);   // IllegalArgumentException si invalide
    public static Map<String, Object> toMap(WorkloadPlan plan);    // forme canonique, clés ordonnées
    public static String hash(WorkloadPlan plan);                  // SHA-256 hex de la forme canonique
}

public final class LoadPresets {
    /** type = RAMP | RAMP_UP_DOWN | CONSTANT | SPIKE | STAIR | SOAK | BURST | CUSTOM */
    public static WorkloadPlan expand(String type, Map<String, Object> parameters);
}

// platform-injection-gatling
package com.performance.platform.injection.gatling.load;
public interface WorkloadPlanTranslator {
    List<OpenInjectionStep> toOpenSteps(WorkloadPlan plan);        // model == OPEN
    List<ClosedInjectionStep> toClosedSteps(WorkloadPlan plan);    // model == CLOSED
}
```

---

## Règles de Comportement

- `measured` vaut `true` par défaut. Durées YAML : `30s`, `5m`, `1h`.
- Un step déclare `workload` OU `loadModel`, jamais les deux. Après parsing, seul `parameters.workload` (forme canonique) existe.
- Les presets reproduisent exactement les étapes produites aujourd'hui par `DefaultLoadModelTranslator` (mêmes clés de paramètres).
- Erreurs de plan = erreurs de scénario au parsing (HTTP 400), plus jamais à l'exécution.
- Sorties de l'executor : `workloadPlan` (map canonique) et `workloadPlanHash`.
- `LoadModel`, `LoadModelType`, `LoadModelTranslator` supprimés en fin de PDR, sans alias.

---

## Issues

| ID | Titre | Module | Priorité | Taille | Bloquée par |
|---|---|---|---|---|---|
| ISSUE-204 | Records WorkloadPlan et codec | `platform-domain` | P0 | M | — |
| ISSUE-205 | Presets : expansion des 8 types | `platform-domain` | P0 | M | ISSUE-204 |
| ISSUE-206 | Parsing YAML de workload et presets | `platform-scenario-yaml` | P0 | M | ISSUE-205 |
| ISSUE-207 | Validation du plan au parsing | `platform-scenario-dsl` | P0 | M | ISSUE-204 |
| ISSUE-208 | Traducteur Gatling du plan (modèle ouvert) | `platform-injection-gatling` | P1 | L | ISSUE-206, ISSUE-201 |
| ISSUE-209 | Modèle fermé (utilisateurs concurrents) | `platform-injection-gatling` | P2 | M | ISSUE-208 |
| ISSUE-210 | Suppression de LoadModel et LoadModelType | `platform-domain` | P2 | S | ISSUE-207, ISSUE-208 |
| ISSUE-211 | Plan résolu et hash dans le rapport | `platform-reporting` | P2 | M | ISSUE-208, ISSUE-245 |
| ISSUE-245 | InjectionReportEntry : plan résolu et hash | `platform-report-api` | P1 | S | ISSUE-208 |

---

## Dépendances Techniques

```
Ce PDR utilise     : PDR-055
Ce PDR est utilisé par : PDR-057, PDR-058, PDR-060, PDR-061, PDR-062
```

---

## Critères de Done (PDR complet)

- [ ] Toutes les Issues du PDR sont DONE
- [ ] Les interfaces sont dans `.claude/workspace/interfaces-registry.md` avec statut STABLE
- [ ] Les tests d'intégration (si applicable) passent
