package com.performance.platform.agent.runtime.lifecycle;

import com.performance.platform.agent.filter.TaskSpecializationFilter;
import com.performance.platform.domain.assertion.AssertionSample;
import com.performance.platform.domain.event.ExecutionLifecycleSignal;
import com.performance.platform.domain.event.LifecycleAction;
import com.performance.platform.domain.id.ExecutionId;
import com.performance.platform.domain.id.TaskId;
import com.performance.platform.domain.task.TaskResult;
import com.performance.platform.plugin.TaskExecutor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

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
    private final Map<String, TaskExecutor> taskExecutors;

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

    /**
     * Preleve un echantillon.
     * <p>
     * Phase A : stub qui cree un AssertionSample basique. La logique reelle
     * de sampling sera implementee ulterieurement par type d'assertion.
     */
    AssertionSample takeSample(SamplingControl control) {
        return new AssertionSample(
                Instant.now(),
                0.0,
                "unknown",
                Map.of("taskName", control.taskName)
        );
    }

    /**
     * Boucle de sampling principale.
     */
    void samplingLoop(SamplingControl control) {
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
            default -> { /* no-op */ }
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

    /**
     * Termine le sampling et produit un TaskResult.
     * <p>
     * Phase A simplification : construit un TaskResult generique avec metadonnees
     * de sampling. L'appel a {@code executor.execute()} et la publication de
     * {@code TaskCompleted} sont differes a une issue future.
     */
    void completeSampling(SamplingControl control) {
        log.info("action=sampling_complete taskId={} taskName={} sampleCount={}",
                control.taskId.value(), control.taskName, control.samples.size());

        Duration duration = Duration.between(control.startedAt, Instant.now());

        TaskResult result = TaskResult.success(
                control.taskId, control.taskName,
                duration,
                Map.of(
                        "samples", control.samples.size(),
                        "samplingStartedAt", control.startedAt.toString(),
                        "samplingDuration", duration.toString()
                )
        );

        log.info("action=sampling_result taskId={} status={} durationMs={}",
                control.taskId.value(), result.status(), duration.toMillis());
    }

    // === Accesseurs package-private pour les tests ===

    int activeSamplingCount() {
        return activeSampling.size();
    }

    boolean hasActiveSampling(String key) {
        return activeSampling.containsKey(key);
    }

    // === SamplingControl (classe interne) ===

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
}
