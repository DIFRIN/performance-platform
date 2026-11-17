# ISSUE-201 — Lanceur de worker et bascule de l'executor

**PDR** : PDR-055
**Module** : `platform-injection-gatling`
**Statut** : APPROVED
**Priorité** : P1
**Bloquée par** : ISSUE-200
**Estime** : L (3-6h)

---

## Objectif

`GatlingTaskExecutor` lance une JVM fille par étape via `InjectionWorkerLauncher` et lit le résultat après la sortie. Plus aucun appel Gatling dans la JVM de l'agent.

> Référence : ADR-066.

## Fichiers à Créer / Modifier

```
infra/platform-injection-gatling/src/main/java/com/performance/platform/injection/gatling/worker/
  ├── InjectionWorkerLauncher.java  — interface
  ├── ProcessInjectionWorkerLauncher.java — ProcessBuilder, classpath, jvm-args, env, logs
  └── WorkerHandle.java             — await / stop / alive
infra/platform-injection-gatling/src/main/java/com/performance/platform/injection/gatling/
  └── GatlingTaskExecutor.java      — utilise le lanceur (modification)
infra/platform-injection-gatling/src/test/java/com/performance/platform/injection/gatling/worker/
  └── ProcessInjectionWorkerLauncherTest.java — sortie 0, sortie != 0, log tail
```

## Interfaces à Implémenter

> Signatures dans `PDR-055` section « Interfaces Publiques » — les appliquer telles quelles.

## Règles Spécifiques

- Classpath = `java.class.path` + répertoire plugins (ADR-007). Binaire Java = `ProcessHandle.current().info().command()`.
- `systemProperties` du step => arguments `-D` du fils uniquement.
- Attente sur Virtual Thread ; stdout/stderr redirigés vers un fichier du run directory.
- Échec => `TaskResult.failed` avec les 50 dernières lignes du log.
- `injection.worker.jvm-args` / `INJECTION_WORKER_JVM_ARGS` ajoutés à la ligne de commande.

## Critères de Done

- [ ] `mvn test -pl :platform-injection-gatling -q` → 0 erreur
- [ ] Aucun `System.setProperty` restant côté agent (grep)
- [ ] Tests existants de `GatlingTaskExecutor` verts via un lanceur de test
- [ ] `.claude/workspace/progress.md` mis à jour : ISSUE-201 → DONE
- [ ] `.claude/workspace/interfaces-registry.md` mis à jour si interfaces concernées
