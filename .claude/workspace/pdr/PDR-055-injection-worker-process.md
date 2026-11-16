# PDR-055 — Injection Worker Process

**Module Maven** : `platform-injection-gatling` (+ platform-plugin-api, platform-agent-runtime)
**Package** : `com.performance.platform.injection.gatling.worker`
**Statut** : WAITING
**Specs de référence** : `docs/knowledge/adr/` ADR-066
**Dépend de** : —
**Issues** : ISSUE-197, ISSUE-198, ISSUE-199, ISSUE-200, ISSUE-201, ISSUE-202, ISSUE-203

---

## Responsabilité

Chaque étape d'injection s'exécute dans une JVM fille dédiée, lancée et supervisée par
`GatlingTaskExecutor`. Le worker isole les propriétés système et l'état statique Gatling, et
rend l'annulation effective (arrêt du processus). Un agent refuse de claim une injection quand
il a atteint `injection.worker.max-concurrent` (défaut 1) — règle intérimaire livrée en premier.

Ne fait PAS : pool de workers réutilisables, conteneur par run, fallback in-process (y compris en LOCAL).

---

## Interfaces Publiques

> Signatures exactes. Ce qui est écrit ici prime sur l'ADR en cas de différence.

```java
// platform-plugin-api — interface optionnelle, TaskExecutor inchangé
package com.performance.platform.plugin;
public interface ExecutorAvailability {
    /** false = l'agent ne doit pas claim une nouvelle tâche pour cet executor. */
    boolean canAcceptNewTask();
}

// platform-injection-gatling
package com.performance.platform.injection.gatling.worker;

public record WorkerRunSpec(Path runDirectory, String simulationId, String simulationClass,
                            Map<String, String> systemProperties, Map<String, String> environment,
                            Duration timeout) {}

public record WorkerOutcome(int exitCode, boolean completed, boolean stoppedByPlatform,
                            Path resultsDirectory, Path resultFile, String logTail) {}

public interface InjectionWorkerLauncher {
    WorkerHandle start(WorkerRunSpec spec);
}
public interface WorkerHandle {
    WorkerOutcome await() throws InterruptedException;   // bloquant, sur Virtual Thread
    void stop(Duration grace);                            // destroy() -> grace -> destroyForcibly()
    boolean alive();
}

public final class InjectionWorkerMain {                  // point d'entrée de la JVM fille
    public static void main(String[] args);               // args[0] = runDirectory
}

@ConfigurationProperties("injection.worker")
public record InjectionWorkerProperties(int maxConcurrent, List<String> jvmArgs, Duration stopGrace) {}
```

---

## Règles de Comportement

- Variables d'env prioritaires (ADR-006) : `INJECTION_WORKER_MAX_CONCURRENT`, `INJECTION_WORKER_JVM_ARGS`, `INJECTION_WORKER_STOP_GRACE`. Défauts : 1, vide, 10s.
- Les secrets passent par l'environnement du processus fils, jamais par un fichier du run directory.
- Classpath du fils = `java.class.path` du parent + répertoire plugins (ADR-007).
- Code de sortie 0 = terminé ; autre = échec, avec les 50 dernières lignes du log dans le `TaskResult`.
- Attente du processus sur Virtual Thread. Aucun `System.setProperty` dans la JVM de l'agent.
- `cleanup(executionId)` arrête réellement le worker ; l'arrêt de l'agent tue ses workers.

---

## Issues

| ID | Titre | Module | Priorité | Taille | Bloquée par |
|---|---|---|---|---|---|
| ISSUE-197 | Interface ExecutorAvailability | `platform-plugin-api` | P0 | S | — |
| ISSUE-198 | Refus de claim si executor indisponible | `platform-agent-runtime` | P0 | M | ISSUE-197 |
| ISSUE-199 | Limite de concurrence d'injection par agent | `platform-injection-gatling` | P0 | S | ISSUE-197 |
| ISSUE-200 | InjectionWorkerMain et run directory | `platform-injection-gatling` | P1 | L | ISSUE-199 |
| ISSUE-201 | Lanceur de worker et bascule de l'executor | `platform-injection-gatling` | P1 | L | ISSUE-200 |
| ISSUE-202 | Annulation et arrêt du worker | `platform-injection-gatling` | P1 | M | ISSUE-201 |
| ISSUE-203 | Test d'intégration worker en mode LOCAL | `platform-bootstrap` | P2 | M | ISSUE-202 |

---

## Dépendances Techniques

```
Ce PDR utilise     : —
Ce PDR est utilisé par : PDR-056, PDR-058, PDR-060, PDR-062
```

---

## Critères de Done (PDR complet)

- [ ] Toutes les Issues du PDR sont DONE
- [ ] Les interfaces sont dans `.claude/workspace/interfaces-registry.md` avec statut STABLE
- [ ] Les tests d'intégration (si applicable) passent
