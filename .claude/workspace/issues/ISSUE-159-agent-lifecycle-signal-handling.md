# ISSUE-159 — Agent Lifecycle Signal Handling

**PDR** : PDR-037
**Module** : `platform-agent-runtime`
**Status** : WAITING
**Priorite** : P0 (bloquant pour les assertions avec linkedTo)
**Bloquee par** : ISSUE-157 (ExecutionLifecycleSignal domain type), ISSUE-158 (Engine lifecycle signal dispatch)
**Estime** : M (1-3h)

---

## Objectif

Implementer la reception et le traitement des `ExecutionLifecycleSignal` par les agents. Sur START, l'agent prepare l'execution de la task (et pour les assertions avec `linkedTo`, demarre une boucle de sampling). Sur STOP, l'agent finalise l'execution (pour les assertions interval, arrete le sampling selon `stopBehavior`, produit l'`AssertionSummary` final, et publie `TaskCompleted`). L'agent ignore silencieusement les signaux pour les tasks dont il n'est pas specialise.

## Fichiers a Creer

```
platform-agent-runtime/src/main/java/com/performance/platform/agent/runtime/
  └── lifecycle/
      └── DefaultLifecycleSignalHandler.java      — traitement des ExecutionLifecycleSignal
```

## Fichiers a Modifier

```
platform-agent-runtime/src/main/java/com/performance/platform/agent/runtime/
  ├── AgentRuntime.java                           — ajouter onLifecycleSignal(ExecutionLifecycleSignal)
  ├── DistributedAgentRuntime.java                — implementer onLifecycleSignal(), deleguer au handler
  └── LocalAgent.java                             — implementer onLifecycleSignal(), deleguer au handler
```

## Structure

### AgentRuntime -- nouvelle methode

```java
// Dans l'interface AgentRuntime (ajout) :
/**
 * Recoit un signal de cycle de vie pour une task.
 * <p>
 * START : Preparer l'execution. Pour les assertions avec linkedTo :
 *         demarrer la boucle de sampling.
 * STOP  : Terminer l'execution. Pour les assertions avec linkedTo :
 *         arreter le sampling selon stopBehavior, produire AssertionSummary final,
 *         publier TaskCompleted.
 * <p>
 * Les signaux pour des tasks non supportees sont ignores silencieusement (log DEBUG).
 */
void onLifecycleSignal(ExecutionLifecycleSignal signal);
```

### DefaultLifecycleSignalHandler

Classe interne a l'agent qui traite les signaux. Logique :

```java
package com.performance.platform.agent.runtime.lifecycle;

import com.performance.platform.agent.runtime.AgentRuntime;
import com.performance.platform.agent.runtime.TaskSpecializationFilter;
import com.performance.platform.domain.event.ExecutionLifecycleSignal;
import com.performance.platform.domain.event.LifecycleAction;
import com.performance.platform.plugin.TaskExecutor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Gere les signaux de cycle de vie recus par un agent.
 * <p>
 * Maintient un registre interne des tasks en cours de monitoring
 * (pour les assertions avec linkedTo) afin de pouvoir les arreter
 * proprement au signal STOP.
 */
public class DefaultLifecycleSignalHandler {

    private static final Logger log = LoggerFactory.getLogger(DefaultLifecycleSignalHandler.class);

    private final TaskSpecializationFilter specializationFilter;
    private final Map<String, TaskExecutor> taskExecutors;  // injecte par l'agent

    // Registre des boucles de sampling actives : executionId::taskId -> controle
    private final Map<String, SamplingControl> activeSampling = new ConcurrentHashMap<>();

    public DefaultLifecycleSignalHandler(
            TaskSpecializationFilter specializationFilter,
            Map<String, TaskExecutor> taskExecutors) {
        this.specializationFilter = specializationFilter;
        this.taskExecutors = taskExecutors;
    }

    /**
     * Traite un ExecutionLifecycleSignal recu.
     */
    public void handle(ExecutionLifecycleSignal signal) {
        String taskName = (String) signal.parameters().get(ExecutionLifecycleSignal.PARAM_TASK_NAME);
        if (taskName == null) {
            log.debug("action=lifecycle_signal_ignored reason=no_taskName signalId={}", signal.id());
            return;
        }

        // Verifier si l'agent est specialise pour cette task
        if (!specializationFilter.canExecute(taskName)) {
            log.debug("action=lifecycle_signal_ignored reason=not_specialized taskName={} signalId={}",
                    taskName, signal.id());
            return;
        }

        String key = signal.executionId().value() + "::" + signal.taskId().value();

        if (signal.action() == LifecycleAction.START) {
            handleStart(signal, taskName, key);
        } else {
            handleStop(signal, taskName, key);
        }
    }

    private void handleStart(ExecutionLifecycleSignal signal, String taskName, String key) {
        log.info("action=lifecycle_start taskId={} taskName={} executionId={}",
                signal.taskId().value(), taskName, signal.executionId().value());

        // Verifier si une boucle est deja active pour cette task (idempotence)
        if (activeSampling.containsKey(key)) {
            log.debug("action=lifecycle_start_ignored reason=already_active taskId={}", signal.taskId().value());
            return;
        }

        // Lire les parametres de sampling
        Object intervalObj = signal.parameters().get(ExecutionLifecycleSignal.PARAM_INTERVAL_SECONDS);
        long intervalSeconds = intervalObj instanceof Long l ? l : 5L; // default 5s
        String stopBehavior = signal.stopBehavior();
        String gracePeriodDuration = (String) signal.parameters()
                .get(ExecutionLifecycleSignal.PARAM_GRACE_PERIOD_DURATION);

        // Creer un controle de sampling
        SamplingControl control = new SamplingControl(
                signal.executionId(), signal.taskId(), taskName,
                intervalSeconds, stopBehavior, gracePeriodDuration
        );

        activeSampling.put(key, control);

        // Demarrer la boucle de sampling dans un Virtual Thread
        Thread.startVirtualThread(() -> samplingLoop(control));
    }

    private void handleStop(ExecutionLifecycleSignal signal, String taskName, String key) {
        log.info("action=lifecycle_stop taskId={} taskName={} executionId={}",
                signal.taskId().value(), taskName, signal.executionId().value());

        SamplingControl control = activeSampling.remove(key);
        if (control == null) {
            // Pas de boucle active -- la task est probablement point-in-time
            // ou le STOP arrive apres un cleanup. Normal, pas d'erreur.
            log.debug("action=lifecycle_stop_ignored reason=no_active_sampling taskId={}",
                    signal.taskId().value());
            return;
        }

        // Mettre a jour le stop behavior depuis le signal (peut etre different de celui du START)
        String stopBehavior = signal.stopBehavior();
        if (!stopBehavior.equals(ExecutionLifecycleSignal.STOP_IMMEDIATE)) {
            control.stopBehavior = stopBehavior;
            control.gracePeriodDuration = (String) signal.parameters()
                    .get(ExecutionLifecycleSignal.PARAM_GRACE_PERIOD_DURATION);
        }

        // Declencher l'arret
        control.stopRequested = true;

        // Le traitement final (AssertionSummary + TaskCompleted) est fait
        // dans la boucle de sampling quand elle detecte le stop
    }

    // ... samplingLoop(), completeSampling() ci-dessous ...
}
```

### SamplingControl (classe interne)

```java
/**
 * Controle d'une boucle de sampling active.
 */
static class SamplingControl {
    final ExecutionId executionId;
    final TaskId taskId;
    final String taskName;
    final long intervalSeconds;
    volatile String stopBehavior;
    volatile String gracePeriodDuration;
    volatile boolean stopRequested = false;

    // Historique des echantillons
    final List<AssertionSample> samples = new CopyOnWriteArrayList<>();
    final Instant startedAt = Instant.now();

    SamplingControl(ExecutionId executionId, TaskId taskId, String taskName,
                    long intervalSeconds, String stopBehavior, String gracePeriodDuration) {
        this.executionId = executionId;
        this.taskId = taskId;
        this.taskName = taskName;
        this.intervalSeconds = intervalSeconds;
        this.stopBehavior = stopBehavior;
        this.gracePeriodDuration = gracePeriodDuration;
    }
}
```

### Boucle de sampling

```java
private void samplingLoop(SamplingControl control) {
    try {
        while (!control.stopRequested) {
            Thread.sleep(Duration.ofSeconds(control.intervalSeconds));

            // Prelever un echantillon
            AssertionSample sample = takeSample(control);
            if (sample != null) {
                control.samples.add(sample);
                log.debug("action=assertion_sample taskId={} value={} unit={} sampleCount={}",
                        control.taskId.value(), sample.observedValue(),
                        sample.unit(), control.samples.size());
            }
        }

        // Appliquer le stop behavior
        applyStopBehavior(control);

        // Produire le resultat final
        completeSampling(control);

    } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        log.warn("action=sampling_interrupted taskId={}", control.taskId.value());
    }
}

private void applyStopBehavior(SamplingControl control) throws InterruptedException {
    switch (control.stopBehavior) {
        case ExecutionLifecycleSignal.STOP_COMPLETE_CURRENT_CYCLE -> {
            // Prendre un dernier echantillon si la boucle etait en attente
            AssertionSample sample = takeSample(control);
            if (sample != null) {
                control.samples.add(sample);
            }
        }
        case ExecutionLifecycleSignal.STOP_GRACE_PERIOD -> {
            // Continuer le sampling pendant gracePeriodDuration
            long graceSeconds = parseGracePeriodDuration(control.gracePeriodDuration);
            long graceEnd = System.currentTimeMillis() + (graceSeconds * 1000);
            while (System.currentTimeMillis() < graceEnd) {
                Thread.sleep(Duration.ofSeconds(control.intervalSeconds));
                AssertionSample sample = takeSample(control);
                if (sample != null) {
                    control.samples.add(sample);
                }
            }
        }
        // STOP_IMMEDIATE : rien de special, la boucle s'arrete immediatement
    }
}

private long parseGracePeriodDuration(String isoDuration) {
    if (isoDuration == null || isoDuration.isBlank()) return 30; // default 30s
    try {
        return Duration.parse(isoDuration).toSeconds();
    } catch (Exception e) {
        log.warn("action=invalid_grace_period value={} using_default=30s", isoDuration);
        return 30;
    }
}
```

### Completion du sampling

```java
private void completeSampling(SamplingControl control) {
    TaskExecutor executor = taskExecutors.get(control.taskName);
    if (executor == null) {
        log.error("action=no_executor_for_sampling taskName={}", control.taskName);
        return;
    }

    TaskResult result;
    if (executor instanceof AssertionExecutor assertionExecutor) {
        // L'assertion executor calcule son resultat a partir de l'historique
        result = assertionExecutor.evaluateFromHistory(
                control.executionId, control.taskId,
                control.samples, control.startedAt
        );
    } else {
        // Fallback : construire un TaskResult generique
        result = TaskResult.success(
                control.taskId, control.taskName,
                Duration.between(control.startedAt, Instant.now()),
                Map.of("samples", control.samples.size())
        );
    }

    // Publier TaskCompleted
    publishTaskCompleted(control.executionId, control.taskId, result);
}
```

**Note sur `evaluateFromHistory()`** : Cette methode n'existe PAS encore sur `AssertionExecutor`. Elle sera ajoutee dans une Issue future (Phase B des assertions interval). Pour cette issue, le `completeSampling()` appelle `execute()` directement (qui fait un `evaluate()` point-in-time standard). La boucle de sampling et l'historique sont construits, mais la production du resultat final est simplifiee pour cette phase.

**Simplification pour Phase A** : Le `completeSampling()` appelle `executor.execute(context, stepDef)` avec le contexte et le step disponibles. L'executor fait son `evaluate()` standard. La boucle de sampling et les `AssertionSample` sont collectes mais le resultat final est un `TaskResult` avec `AssertionSummary` en outputs (via le bridge `AssertionExecutor.execute()`).

### DistributedAgentRuntime -- modification

```java
// Dans DistributedAgentRuntime :
@Override
public void onLifecycleSignal(ExecutionLifecycleSignal signal) {
    lifecycleSignalHandler.handle(signal);
}
```

Le `DefaultLifecycleSignalHandler` est cree au constructeur avec `specializationFilter` et `taskExecutors`.

### LocalAgent -- modification

```java
// Dans LocalAgent :
@Override
public void onLifecycleSignal(ExecutionLifecycleSignal signal) {
    lifecycleSignalHandler.handle(signal);
}
```

## Regles Specifiques

- L'agent ignore silencieusement les signaux pour les tasks non supportees (verification via `specializationFilter.canExecute(taskName)`).
- Idempotence START : si un signal START est recu pour une task deja en cours de monitoring, il est ignore (log DEBUG).
- STOP sans START actif : normal pour les tasks point-in-time, log DEBUG.
- La boucle de sampling utilise `Thread.startVirtualThread()` (Virtual Threads) pour ne pas bloquer le thread principal de l'agent.
- L'historique des echantillons utilise `CopyOnWriteArrayList` pour la thread safety (ecrit par la boucle, lu potentiellement par le thread STOP).
- `stopBehavior` par defaut = `"immediate"` (deja implemente dans `ExecutionLifecycleSignal.stopBehavior()`).
- `gracePeriodDuration` par defaut = 30 secondes si non parseable.
- Le `SamplingControl` est retiré du `activeSampling` des que le STOP est recu (pour eviter les fuites memoire).
- **Phase A simplification** : Pour cette issue, le `completeSampling()` appelle `executor.execute(context, stepDef)` comme pour une assertion point-in-time. L'historique collecte est inclus dans les outputs mais le verdict est base sur le dernier `evaluate()`. La methode `evaluateFromHistory()` sera ajoutee ulterieurement pour les assertions interval completes.

## Tests

Fichier a creer :
```
platform-agent-runtime/src/test/java/com/performance/platform/agent/runtime/lifecycle/
  └── DefaultLifecycleSignalHandlerTest.java
```

Tests unitaires minimum :
1. `shouldIgnoreSignalForUnsupportedTask` -- signal avec taskName non supporte -> ignore
2. `shouldIgnoreStartIfAlreadyActive` -- deuxieme START pour meme taskId -> ignore (idempotence)
3. `shouldStartSamplingOnStartSignal` -- START cree un SamplingControl et demarre la boucle
4. `shouldStopSamplingOnStopSignal` -- STOP avec stopBehavior=immediate arrete la boucle
5. `shouldApplyCompleteCurrentCycleBehavior` -- STOP avec completeCurrentCycle prend un dernier echantillon
6. `shouldApplyGracePeriodBehavior` -- STOP avec gracePeriod continue le sampling
7. `shouldDefaultToImmediateIfNoStopBehavior` -- STOP sans stopBehavior -> immediate
8. `shouldIgnoreStopIfNoActiveSampling` -- STOP sans START prealable -> ignore (pas d'erreur)
9. `shouldCollectSamplesDuringInterval` -- la boucle collecte des AssertionSample a chaque intervalSeconds
10. `shouldCleanupActiveSamplingOnStop` -- apres STOP, la cle est retiree de activeSampling
11. `shouldCallExecutorExecuteOnComplete` -- completeSampling appelle executor.execute()
12. `shouldHandleNullTaskName` -- signal sans taskName -> ignore sans erreur

## Critères de Done

- [ ] `AgentRuntime.onLifecycleSignal()` ajoute a l'interface
- [ ] `DistributedAgentRuntime` implemente `onLifecycleSignal()`
- [ ] `LocalAgent` implemente `onLifecycleSignal()`
- [ ] `DefaultLifecycleSignalHandler` cree et teste
- [ ] Boucle de sampling avec Virtual Threads
- [ ] Support des 3 stop behaviors : immediate, completeCurrentCycle, gracePeriod
- [ ] Idempotence START et robustesse STOP sans START
- [ ] `mvn test -pl platform-agent-runtime -q` -> 0 erreur
- [ ] `.claude/workspace/progress.md` mis a jour : ISSUE-159 -> DONE
- [ ] `.claude/workspace/interfaces-registry.md` mis a jour : `AgentRuntime.onLifecycleSignal`, `DefaultLifecycleSignalHandler`
