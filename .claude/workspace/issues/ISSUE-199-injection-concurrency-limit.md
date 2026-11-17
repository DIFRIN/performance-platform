# ISSUE-199 — Limite de concurrence d'injection par agent

**PDR** : PDR-055
**Module** : `platform-injection-gatling`
**Statut** : APPROVED
**Priorité** : P0
**Bloquée par** : ISSUE-197
**Estime** : S (< 1h)

---

## Objectif

Règle intérimaire ADR-066 : `GatlingTaskExecutor` implémente `ExecutorAvailability` et refuse au-delà de `injection.worker.max-concurrent` (défaut 1). Supprime la corruption de propriétés système entre runs concurrents.

> Référence : ADR-066.

## Fichiers à Créer / Modifier

```
infra/platform-injection-gatling/src/main/java/com/performance/platform/injection/gatling/
  ├── GatlingTaskExecutor.java      — implémente ExecutorAvailability (modification)
  └── worker/InjectionWorkerProperties.java — propriétés `injection.worker.*` (création)
infra/platform-injection-gatling/src/test/java/com/performance/platform/injection/gatling/
  └── GatlingTaskExecutorAvailabilityTest.java
```

## Interfaces à Implémenter

> Signatures dans `PDR-055` section « Interfaces Publiques » — les appliquer telles quelles.

## Règles Spécifiques

- Compteur d'exécutions actives thread-safe ; décrément garanti en `finally`.
- Env var `INJECTION_WORKER_MAX_CONCURRENT` prioritaire sur la property (ADR-006).

## Critères de Done

- [ ] `mvn test -pl :platform-injection-gatling -q` → 0 erreur
- [ ] Deuxième injection simultanée refusée avec max-concurrent=1
- [ ] Compteur revenu à 0 après succès, échec et timeout
- [ ] `.claude/workspace/progress.md` mis à jour : ISSUE-199 → DONE
- [ ] `.claude/workspace/interfaces-registry.md` mis à jour si interfaces concernées
