# PDR-057 — Injection Capabilities & Guardrails

**Module Maven** : `platform-plugin-api` (+ platform-injection-gatling)
**Package** : `com.performance.platform.plugin`
**Statut** : WAITING
**Specs de référence** : `docs/knowledge/adr/` ADR-065
**Dépend de** : PDR-056
**Issues** : ISSUE-212, ISSUE-213, ISSUE-214

---

## Responsabilité

Chaque adaptateur d'injection déclare ce qu'il sait exécuter fidèlement et rejette le reste avant la
première arrivée, sans approximation. Les simulations Java custom sont « opaques » sauf opt-in. Un garde-fou
par agent plafonne le débit accepté.

Ne fait PAS : validation des capacités côté orchestrateur (ADR-057), second moteur d'injection.

---

## Interfaces Publiques

> Signatures exactes. Ce qui est écrit ici prime sur l'ADR en cas de différence.

```java
package com.performance.platform.plugin;

public record InjectionCapabilities(
        Set<ArrivalModel> models,
        Set<String> stageKinds,          // "ramp", "hold", "burst", "pause"
        Set<String> protocols,
        boolean supportsGlobalScope,
        boolean supportsCancellation,
        boolean providesHistogram,
        boolean providesWindows) {}

public interface InjectionCapabilityProvider {
    InjectionCapabilities injectionCapabilities();
}

// platform-injection-gatling
package com.performance.platform.injection.gatling.load;
public final class PlanAdmission {
    /** Lève PlanRejectedException (message nommant la fonctionnalité) si le plan n'est pas exécutable. */
    public static void check(WorkloadPlan plan, InjectionCapabilities caps, InjectionLimits limits);
}
@ConfigurationProperties("injection")
public record InjectionLimits(Double maxArrivalRate, Long maxConcurrentUsers) {}   // null = pas de limite
```

---

## Règles de Comportement

- Rejeter, ne jamais approximer. Le contrôle s'exécute sur l'agent avant tout trafic.
- Simulation custom sans `InjectionCapabilityProvider` = opaque : `scope: global` rejeté, sortie `opaque=true`.
- Env vars prioritaires : `INJECTION_MAX_ARRIVAL_RATE`, `INJECTION_MAX_CONCURRENT_USERS`.
- Le garde-fou compare la part de l'agent (après mise à l'échelle par le share) aux limites.

---

## Issues

| ID | Titre | Module | Priorité | Taille | Bloquée par |
|---|---|---|---|---|---|
| ISSUE-212 | InjectionCapabilities dans plugin-api | `platform-plugin-api` | P1 | S | ISSUE-204 |
| ISSUE-213 | Admission du plan et simulations opaques | `platform-injection-gatling` | P1 | M | ISSUE-212, ISSUE-208 |
| ISSUE-214 | Garde-fou de débit par agent | `platform-injection-gatling` | P2 | S | ISSUE-213 |

---

## Dépendances Techniques

```
Ce PDR utilise     : PDR-056
Ce PDR est utilisé par : PDR-060, PDR-062
```

---

## Critères de Done (PDR complet)

- [ ] Toutes les Issues du PDR sont DONE
- [ ] Les interfaces sont dans `.claude/workspace/interfaces-registry.md` avec statut STABLE
- [ ] Les tests d'intégration (si applicable) passent
