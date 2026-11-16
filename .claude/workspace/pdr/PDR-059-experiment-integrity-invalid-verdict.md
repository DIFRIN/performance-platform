# PDR-059 — Experiment Integrity & INVALID Verdict

**Module Maven** : `platform-domain` (+ platform-assertion, platform-reporting, platform-report-renderers, platform-bootstrap)
**Package** : `com.performance.platform.domain.report`
**Statut** : WAITING
**Specs de référence** : `docs/knowledge/adr/` ADR-069
**Dépend de** : PDR-058
**Issues** : ISSUE-221, ISSUE-222, ISSUE-223, ISSUE-224, ISSUE-225, ISSUE-226

---

## Responsabilité

Un run qui n'a pas appliqué la charge décrite (sous-livraison, agent perdu, arrêt anticipé) reçoit le
verdict `INVALID` au lieu de `SUCCESS` ou `FAILED`. Les contrôles d'intégrité sont produits automatiquement
pour chaque étape d'injection ; seules leurs tolérances sont configurables.

Ne fait PAS : changement de `ExecutionStatus` (l'exécution reste `COMPLETED`).

---

## Interfaces Publiques

> Signatures exactes. Ce qui est écrit ici prime sur l'ADR en cas de différence.

```java
package com.performance.platform.domain.report;
public enum Verdict { SUCCESS, WARNING, FAILED, INVALID }    // priorité : INVALID > FAILED > WARNING > SUCCESS

package com.performance.platform.domain.assertion;
// AssertionFamily gagne INTEGRITY
public record IntegrityTolerances(double maxArrivalDeviationPercent, double maxGeneratorCpuPercent) {
    public static IntegrityTolerances defaults();      // 5.0, 90.0
}

package com.performance.platform.assertion;
public final class IntegrityCheckFactory {
    /** Contrôles : arrival-deviation, agent-loss, incomplete-run, generator-saturation (WARNING seulement). */
    public static List<Check> checks(IntegrityInput input, IntegrityTolerances tolerances);
}
public record IntegrityInput(double arrivalDeviationPercent, boolean arrivalKnown, int grantedAgents,
                             int reportingAgents, boolean complete, double generatorCpuPercent) {}
```

---

## Règles de Comportement

- Un contrôle d'intégrité FAILED => verdict INVALID. Saturation seule => WARNING.
- Simulation opaque : arrival-deviation `INCONCLUSIVE` (revue humaine), pas INVALID.
- Étape échouée avant toute charge (plan rejeté, `minAgents` non atteint) : exécution FAILED, pas INVALID.
- Les contrôles ne sont pas désactivables ; la tolérance utilisée apparaît dans le rapport.
- CLI (ADR-021) : INVALID => code de sortie 2.

---

## Issues

| ID | Titre | Module | Priorité | Taille | Bloquée par |
|---|---|---|---|---|---|
| ISSUE-221 | Verdict INVALID et famille INTEGRITY | `platform-domain` | P0 | S | ISSUE-216 |
| ISSUE-222 | Bloc integrity : validation scénario | `platform-scenario-dsl` | P2 | S | ISSUE-221, ISSUE-207 |
| ISSUE-223 | IntegrityCheckFactory | `platform-assertion` | P1 | M | ISSUE-221, ISSUE-219 |
| ISSUE-224 | Verdict INVALID dans le moteur de rapport | `platform-reporting` | P1 | M | ISSUE-223 |
| ISSUE-225 | Rendu HTML/PDF du verdict INVALID | `platform-report-renderers` | P2 | S | ISSUE-224 |
| ISSUE-226 | API, IHM et CLI : verdict INVALID | `platform-bootstrap` | P2 | M | ISSUE-224 |

---

## Dépendances Techniques

```
Ce PDR utilise     : PDR-058
Ce PDR est utilisé par : PDR-062
```

---

## Critères de Done (PDR complet)

- [ ] Toutes les Issues du PDR sont DONE
- [ ] Les interfaces sont dans `.claude/workspace/interfaces-registry.md` avec statut STABLE
- [ ] Les tests d'intégration (si applicable) passent
