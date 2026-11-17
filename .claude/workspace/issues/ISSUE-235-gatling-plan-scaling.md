# ISSUE-235 — Mise à l'échelle du plan par le share

**PDR** : PDR-060
**Module** : `platform-injection-gatling`
**Statut** : DONE
**Priorité** : P1
**Bloquée par** : ISSUE-234, ISSUE-213
**Estime** : M (1-3h)

---

## Objectif

Avec `scope: global`, l'adaptateur met le plan à l'échelle de sa part avant admission et exécution.

> Référence : ADR-067.

## Fichiers à Créer / Modifier

```
infra/platform-injection-gatling/src/main/java/com/performance/platform/injection/gatling/load/PlanScaler.java         — (création)
infra/platform-injection-gatling/src/main/java/com/performance/platform/injection/gatling/GatlingTaskExecutor.java      — scale → admission → worker (modification)
infra/platform-injection-gatling/src/test/java/com/performance/platform/injection/gatling/load/PlanScalerTest.java
```

## Interfaces à Implémenter

> Signatures dans `PDR-060` section « Interfaces Publiques » — les appliquer telles quelles.

## Règles Spécifiques

- Niveaux et débits x `fraction`. Entiers (`Burst.users`, population fermée) : plus fort reste selon `index`.
- Propriété testée : pour tout `count` de 1 à 16, la somme des parts entières = valeur globale.
- Part nulle sur toutes les étapes : pas de worker, résultat vide `complete=true`.
- Déclarer `supportsGlobalScope=true`. Sorties : `shareIndex`, `shareCount`, plan mis à l'échelle + hash du plan global.

## Critères de Done

- [ ] `mvn test -pl :platform-injection-gatling -q` → 0 erreur
- [ ] 4 agents, 20 000 users/s global => 5 000 chacun
- [ ] Burst de 10 users sur 4 agents => 3,3,2,2
- [ ] `.claude/workspace/progress.md` mis à jour : ISSUE-235 → DONE
- [ ] `.claude/workspace/interfaces-registry.md` mis à jour si interfaces concernées
