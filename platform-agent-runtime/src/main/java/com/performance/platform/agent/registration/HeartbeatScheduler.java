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

/**
 * Planificateur de heartbeat périodique pour un agent.
 * <p>
 * Utilise un {@link ScheduledExecutorService} sur Virtual Threads pour
 * envoyer périodiquement des heartbeats via {@link AgentRegistrationPort}.
 * <p>
 * Garantit que {@code ttl >= 3 × intervalSeconds} pour éviter les
 * expirations intempestives côté orchestrateur.
 */
public class HeartbeatScheduler {

    private static final Logger log = LoggerFactory.getLogger(HeartbeatScheduler.class);

    private final AgentRegistrationPort registrationPort;
    private final AgentId agentId;
    private final int intervalSeconds;
    private final int activeTaskCount;
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
     * @param activeTaskCount  le nombre de tâches actives (pour le heartbeat initial, 0)
     */
    public HeartbeatScheduler(AgentRegistrationPort registrationPort,
                              AgentId agentId,
                              int intervalSeconds,
                              int activeTaskCount) {
        this.registrationPort = Objects.requireNonNull(registrationPort, "registrationPort must not be null");
        this.agentId = Objects.requireNonNull(agentId, "agentId must not be null");
        if (intervalSeconds < 1) {
            throw new IllegalArgumentException("intervalSeconds must be >= 1, got " + intervalSeconds);
        }
        this.intervalSeconds = intervalSeconds;
        this.activeTaskCount = activeTaskCount;
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
     * Arrête le planificateur. Les heartbeats en cours peuvent se terminer.
     */
    public void stop() {
        log.info("action=heartbeat_scheduler_stop agentId={} totalHeartbeats={}",
                agentId.value(), heartbeatCount.get());
        var f = future.getAndSet(null);
        if (f != null) {
            f.cancel(false);
        }
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
                    AgentState.IDLE,
                    activeTaskCount,
                    Instant.now()
            );
            registrationPort.sendHeartbeat(agentId, heartbeat);
            heartbeatCount.incrementAndGet();
        } catch (RegistrationException e) {
            // Le heartbeat suivant réessaiera — on ne casse pas le schedule
            Thread.currentThread().interrupt();
        }
    }
}
