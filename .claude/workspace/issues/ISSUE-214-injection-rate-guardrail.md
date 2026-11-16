# ISSUE-214 — Garde-fou de débit par agent

**PDR** : PDR-057
**Module** : `platform-injection-gatling`
**Statut** : WAITING
**Priorité** : P2
**Bloquée par** : ISSUE-213
**Estime** : S (< 1h)

---

## Objectif

Rejeter un plan dont le pic dépasse `injection.max-arrival-rate` (ouvert) ou `injection.max-concurrent-users` (fermé).

> Référence : ADR-065.

## Fichiers à Créer / Modifier

```
infra/platform-injection-gatling/src/main/java/com/performance/platform/injection/gatling/load/
  ├── InjectionLimits.java
  └── PlanAdmission.java            — contrôle des limites (modification)
infra/platform-injection-gatling/src/test/java/com/performance/platform/injection/gatling/load/InjectionLimitsTest.java
```

## Interfaces à Implémenter

> Signatures dans `PDR-057` section « Interfaces Publiques » — les appliquer telles quelles.

## Règles Spécifiques

- Env vars prioritaires : `INJECTION_MAX_ARRIVAL_RATE`, `INJECTION_MAX_CONCURRENT_USERS` (ADR-006).
- Pic = plus haut niveau de `Ramp`/`Hold` ; `Burst` comparé à `max-concurrent-users`.
- Non défini = pas de limite. Message : valeur demandée, limite, agent.

## Critères de Done

- [ ] `mvn test -pl :platform-injection-gatling -q` → 0 erreur
- [ ] Plan au-dessus de la limite rejeté ; égal à la limite accepté
- [ ] `.claude/workspace/progress.md` mis à jour : ISSUE-214 → DONE
- [ ] `.claude/workspace/interfaces-registry.md` mis à jour si interfaces concernées
