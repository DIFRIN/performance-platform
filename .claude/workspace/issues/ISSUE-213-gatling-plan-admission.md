# ISSUE-213 — Admission du plan et simulations opaques

**PDR** : PDR-057
**Module** : `platform-injection-gatling`
**Statut** : DONE
**Priorité** : P1
**Bloquée par** : ISSUE-212, ISSUE-208
**Estime** : M (1-3h)

---

## Objectif

L'adaptateur Gatling déclare ses capacités et rejette tout plan non supporté avant de lancer le worker. Les simulations custom sans opt-in sont marquées opaques.

> Référence : ADR-065.

## Fichiers à Créer / Modifier

```
infra/platform-injection-gatling/src/main/java/com/performance/platform/injection/gatling/load/
  ├── PlanAdmission.java
  └── PlanRejectedException.java
infra/platform-injection-gatling/src/main/java/com/performance/platform/injection/gatling/GatlingTaskExecutor.java — implémente InjectionCapabilityProvider, appelle PlanAdmission (modification)
infra/platform-injection-gatling/src/test/java/com/performance/platform/injection/gatling/load/PlanAdmissionTest.java
```

## Interfaces à Implémenter

> Signatures dans `PDR-057` section « Interfaces Publiques » — les appliquer telles quelles.

## Règles Spécifiques

- Capacités déclarées : modèles réellement supportés à la date de l'Issue, étapes `ramp/hold/burst/pause`, protocoles de `ProtocolSupportInfo`.
- `supportsGlobalScope=false` jusqu'au PDR Grant Share ; `providesHistogram`/`providesWindows=false` jusqu'au PDR Measurements.
- Simulation custom n'implémentant pas `InjectionCapabilityProvider` : sortie `opaque=true`, `scope: global` rejeté.
- Rejet = `TaskResult.failed` avec le nom de la fonctionnalité ; aucun worker lancé.

## Critères de Done

- [ ] `mvn test -pl :platform-injection-gatling -q` → 0 erreur
- [ ] Plan non supporté => échec avant tout trafic
- [ ] Simulation custom => `opaque=true` dans les sorties
- [ ] `.claude/workspace/progress.md` mis à jour : ISSUE-213 → DONE
- [ ] `.claude/workspace/interfaces-registry.md` mis à jour si interfaces concernées
