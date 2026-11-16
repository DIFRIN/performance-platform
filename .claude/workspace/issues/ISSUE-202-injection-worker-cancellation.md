# ISSUE-202 — Annulation et arrêt du worker

**PDR** : PDR-055
**Module** : `platform-injection-gatling`
**Statut** : WAITING
**Priorité** : P1
**Bloquée par** : ISSUE-201
**Estime** : M (1-3h)

---

## Objectif

Annulation, timeout et `cleanup()` arrêtent réellement la charge : `destroy()`, attente `stop-grace`, puis `destroyForcibly()`.

> Référence : ADR-066.

## Fichiers à Créer / Modifier

```
infra/platform-injection-gatling/src/main/java/com/performance/platform/injection/gatling/
  └── GatlingTaskExecutor.java      — cleanup() arrête les workers de l'exécution (modification)
infra/platform-injection-gatling/src/main/java/com/performance/platform/injection/gatling/worker/
  └── ProcessInjectionWorkerLauncher.java — stop(grace) + hook d'arrêt agent (modification)
infra/platform-injection-gatling/src/test/java/com/performance/platform/injection/gatling/worker/
  └── WorkerCancellationTest.java   — processus long arrêté dans le délai
```

## Interfaces à Implémenter

> Signatures dans `PDR-055` section « Interfaces Publiques » — les appliquer telles quelles.

## Règles Spécifiques

- `injection.worker.stop-grace` défaut 10s.
- Résultat marqué incomplet (`stoppedByPlatform=true`) ; sortie `complete=false`.
- `@PreDestroy` : tous les workers vivants sont arrêtés.
- Log : `action=worker_stopped executionId={} taskId={} forced={}`.

## Critères de Done

- [ ] `mvn test -pl :platform-injection-gatling -q` → 0 erreur
- [ ] `cleanup(executionId)` termine le processus en moins de `stop-grace` + 2s
- [ ] Timeout de step => worker arrêté, `TaskResult` en échec explicite
- [ ] `.claude/workspace/progress.md` mis à jour : ISSUE-202 → DONE
- [ ] `.claude/workspace/interfaces-registry.md` mis à jour si interfaces concernées
