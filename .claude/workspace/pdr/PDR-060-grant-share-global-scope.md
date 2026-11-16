# PDR-060 — Grant Share & Global Load Scope

**Module Maven** : `platform-domain` (+ platform-transport, platform-execution-engine, platform-agent-runtime, platform-scenario-yaml, platform-scenario-dsl, platform-injection-gatling)
**Package** : `com.performance.platform.domain.event`
**Statut** : WAITING
**Specs de référence** : `docs/knowledge/adr/` ADR-067
**Dépend de** : PDR-056, PDR-057, PDR-055
**Issues** : ISSUE-227, ISSUE-228, ISSUE-229, ISSUE-230, ISSUE-231, ISSUE-232, ISSUE-233, ISSUE-234, ISSUE-235, ISSUE-236, ISSUE-237

---

## Responsabilité

Le grant porte une part générique `{index, count, fraction}`. Avec `scope: global`, chaque agent met le
plan à l'échelle de sa part : la charge totale est définie par le scénario et non par le nombre d'agents.
Ajoute `minAgents` et `onAgentLoss: abort | continue`.

Ne fait PAS : pondération par capacité, réallocation après perte d'agent, barrière de démarrage synchronisé.
L'orchestrateur ne connaît aucune notion de débit (ADR-045).

---

## Interfaces Publiques

> Signatures exactes. Ce qui est écrit ici prime sur l'ADR en cas de différence.

```java
package com.performance.platform.domain.execution;
public record GrantShare(int index, int count, double fraction) {
    public static GrantShare single();                 // (0, 1, 1.0)
    public static GrantShare equal(int index, int count);
}

package com.performance.platform.domain.event;
// TaskGrantSignal gagne : GrantShare share (non null ; constructeur de compatibilité -> single())

package com.performance.platform.domain.execution;
// ExecutionContext gagne : GrantShare share() en lecture seule ; with(...) le conserve ;
// ExecutionContext withShare(GrantShare share)

package com.performance.platform.domain.scenario;
public enum AgentLossPolicy { ABORT, CONTINUE }
// StepDefinition gagne : int minAgents (défaut 1), AgentLossPolicy onAgentLoss (défaut ABORT)

package com.performance.platform.injection.gatling.load;
public final class PlanScaler {
    /** PER_AGENT : plan inchangé. GLOBAL : niveaux x fraction ; entiers répartis au plus fort reste selon index. */
    public static WorkloadPlan scale(WorkloadPlan plan, GrantShare share);
    public static long integerShare(long total, GrantShare share);
}
```

---

## Règles de Comportement

- `fanOut: one` => `GrantShare.single()`. `fanOut: all` => agents triés par `AgentId`, fractions égales.
- `minAgents` vérifié avant l'envoi du moindre grant : si non atteint, aucun grant, étape FAILED.
- `scope: global` exige `fanOut: all` (validation scénario) et `supportsGlobalScope` (agent).
- Somme des parts entières = valeur globale exacte. Part nulle sur toutes les étapes : contribution vide, pas une erreur.
- Compteurs de templates : `index + n x count`. Graine = hash du plan + index.
- `abort` : étape FAILED et annulation des agents restants. `continue` : parts inchangées, intégrité signalée (PDR Integrity).
- Chaque agent démarre au grant et enregistre `startedAt` ; aucune synchronisation d'horloge.
- Orchestrateur et agents se mettent à jour ensemble (changement du jeu de messages, Spec 05 §3).

---

## Issues

| ID | Titre | Module | Priorité | Taille | Bloquée par |
|---|---|---|---|---|---|
| ISSUE-227 | GrantShare, signal de grant et contexte | `platform-domain` | P0 | M | — |
| ISSUE-228 | StepDefinition : minAgents et onAgentLoss | `platform-domain` | P0 | S | — |
| ISSUE-229 | Parsing YAML : scope, minAgents, onAgentLoss | `platform-scenario-yaml` | P1 | S | ISSUE-228, ISSUE-206 |
| ISSUE-230 | Validation : scope global et minAgents | `platform-scenario-dsl` | P1 | S | ISSUE-228, ISSUE-207 |
| ISSUE-231 | Codec transport du GrantShare | `platform-transport` | P1 | S | ISSUE-227 |
| ISSUE-232 | Moteur : calcul des parts et minAgents | `platform-execution-engine` | P1 | M | ISSUE-227, ISSUE-228 |
| ISSUE-233 | Moteur : politique de perte d'agent | `platform-execution-engine` | P2 | M | ISSUE-232, ISSUE-202 |
| ISSUE-234 | Agent : propagation du share à l'executor | `platform-agent-runtime` | P1 | S | ISSUE-227, ISSUE-231 |
| ISSUE-235 | Mise à l'échelle du plan par le share | `platform-injection-gatling` | P1 | M | ISSUE-234, ISSUE-213 |
| ISSUE-236 | Compteurs partitionnés, graines et garde-fou sur la part | `platform-injection-gatling` | P2 | M | ISSUE-235, ISSUE-214 |
| ISSUE-237 | Test d'intégration : scope global distribué | `platform-bootstrap` | P2 | M | ISSUE-233, ISSUE-236 |

---

## Dépendances Techniques

```
Ce PDR utilise     : PDR-056, PDR-057, PDR-055
Ce PDR est utilisé par : PDR-062
```

---

## Critères de Done (PDR complet)

- [ ] Toutes les Issues du PDR sont DONE
- [ ] Les interfaces sont dans `.claude/workspace/interfaces-registry.md` avec statut STABLE
- [ ] Les tests d'intégration (si applicable) passent
