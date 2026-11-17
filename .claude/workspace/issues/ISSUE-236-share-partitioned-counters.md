# ISSUE-236 — Compteurs partitionnés, graines et garde-fou sur la part

**PDR** : PDR-060
**Module** : `platform-injection-gatling`
**Statut** : APPROVED
**Priorité** : P2
**Bloquée par** : ISSUE-235, ISSUE-214
**Estime** : M (1-3h)

---

## Objectif

Les compteurs de templates produisent des valeurs disjointes entre agents ; les graines dérivent du plan et de l'index ; le garde-fou s'applique à la part.

> Référence : ADR-067.

## Fichiers à Créer / Modifier

```
infra/platform-injection-gatling/src/main/java/com/performance/platform/injection/gatling/http/HttpScenarioSimulation.java, HttpRequestRenderer.java — compteur `index + n x count` (modification)
infra/platform-injection-gatling/src/main/java/com/performance/platform/injection/gatling/kafka/ — idem (modification)
infra/platform-injection-gatling/src/main/java/com/performance/platform/injection/gatling/load/PlanAdmission.java     — limites appliquées au plan mis à l'échelle (modification)
(+ tests)
```

## Interfaces à Implémenter

> Signatures dans `PDR-060` section « Interfaces Publiques » — les appliquer telles quelles.

## Règles Spécifiques

- Avec `GrantShare.single()` le comportement est identique à aujourd'hui.
- Graine = 64 premiers bits du hash du plan XOR index.
- Share transmis au worker par le run directory.

## Critères de Done

- [ ] `mvn test -pl :platform-injection-gatling -q` → 0 erreur
- [ ] Deux agents (0,2) et (1,2) : aucun `{index}` commun sur 1000 tirages
- [ ] `.claude/workspace/progress.md` mis à jour : ISSUE-236 → DONE
- [ ] `.claude/workspace/interfaces-registry.md` mis à jour si interfaces concernées
