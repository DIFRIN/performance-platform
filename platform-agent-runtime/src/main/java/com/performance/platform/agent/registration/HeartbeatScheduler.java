package com.performance.platform.agent.registration;

import com.performance.platform.domain.agent.AgentHeartbeat;
import com.performance.platform.domain.agent.AgentState;
import com.performance.platform.domain.id.AgentId;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.Objects;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

/**
 * Planificateur de heartbeat périodique pour un agent.
 * <p>
 * Utilise un {@link ScheduledExecutorService} sur Virtual Threads pour
 * envoyer périodiquement des heartbeats via {@link AgentRegistrationPort}.
 * <p>
 * Garantit que {@code ttl >= 3 × intervalSeconds} pour éviter les
 * expirations intempestives côté orchestrateur.
 * <p>
 * Les {@link Supplier} d'état et de tâches actives permettent à
 * {@code DistributedAgentRuntime} de fournir l'état réel à chaque heartbeat.
 */
public class HeartbeatScheduler {

    private static final Logger log = LoggerFactory.getLogger(HeartbeatScheduler.class);

    private final AgentRegistrationPort registrationPort;
    private final AgentId agentId;
    private final int intervalSeconds;
    private final Supplier<AgentState> stateSupplier;
    private final Supplier<Integer> activeTasksSupplier;
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(
            Thread.ofVirtual().factory()
    );

    private final AtomicReference<ScheduledFuture<?>> future = new AtomicReference<>();
    private final AtomicInteger heartbeatCount = new AtomicInteger(0);

    /**
     * Construit un planificateur de heartbeat.
     *
     * @param registrationPort le port d'enregistrement
     * @param agentId          l'identifiant de l'agent local
     * @param intervalSeconds  l'intervalle entre deux heartbeats (≥ 1)
     * @param stateSupplier    fournit l'état courant de l'agent à chaque heartbeat
     * @param activeTasksSupplier fournit le nombre de tâches actives à chaque heartbeat
     */
    public HeartbeatScheduler(AgentRegistrationPort registrationPort,
                              AgentId agentId,
                              int intervalSeconds,
                              Supplier<AgentState> stateSupplier,
                              Supplier<Integer> activeTasksSupplier) {
        this.registrationPort = Objects.requireNonNull(registrationPort, "registrationPort must not be null");
        this.agentId = Objects.requireNonNull(agentId, "agentId must not be null");
        if (intervalSeconds < 1) {
            throw new IllegalArgumentException("intervalSeconds must be >= 1, got " + intervalSeconds);
        }
        this.intervalSeconds = intervalSeconds;
        this.stateSupplier = Objects.requireNonNull(stateSupplier, "stateSupplier must not be null");
        this.activeTasksSupplier = Objects.requireNonNull(activeTasksSupplier, "activeTasksSupplier must not be null");
    }

    /**
     * Démarre l'envoi périodique des heartbeats.
     * <p>
     * Le premier heartbeat est envoyé immédiatement, puis toutes les
     * {@code intervalSeconds} secondes.
     */
    public void start() {
        log.info("action=heartbeat_scheduler_start agentId={} intervalSeconds={} ttlSeconds={}",
                agentId.value(), intervalSeconds, registrationTtlSeconds());
        var f = scheduler.scheduleAtFixedRate(
                this::sendHeartbeat,
                0,
                intervalSeconds,
                TimeUnit.SECONDS
        );
        future.set(f);
    }

    /**
     * Arrête le planificateur. Annule les tâches futures et ferme le scheduler.
     */
    public void stop() {
        log.info("action=heartbeat_scheduler_stop agentId={} totalHeartbeats={}",
                agentId.value(), heartbeatCount.get());
        var f = future.getAndSet(null);
        if (f != null) {
            f.cancel(false);
        }
        scheduler.shutdown();
    }

    /**
     * Retourne la TTL recommandée pour l'enregistrement de l'agent.
     * <p>
     * Garantie : {@code ttl >= 3 × intervalSeconds}.
     *
     * @return la TTL en secondes
     */
    public long registrationTtlSeconds() {
        return 3L * intervalSeconds;
    }

    /**
     * Retourne le nombre de heartbeats envoyés depuis le démarrage.
     */
    public int heartbeatCount() {
        return heartbeatCount.get();
    }

    /**
     * Retourne {@code true} si le planificateur est en cours d'exécution.
     */
    public boolean isRunning() {
        var f = future.get();
        return f != null && !f.isDone();
    }

    private void sendHeartbeat() {
        try {
            var heartbeat = new AgentHeartbeat(
                    agentId,
                    stateSupplier.get(),
                    activeTasksSupplier.get(),
                    Instant.now()
            );
            registrationPort.sendHeartbeat(agentId, heartbeat);
            heartbeatCount.incrementAndGet();
        } catch (RegistrationException e) {
            log.warn("action=heartbeat_failed agentId={} will retry on next interval",
                    agentId.value(), e);
            // Pas d'interruption — le schedule continue naturellement
        }
    }
}
