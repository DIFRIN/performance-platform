package com.performance.platform.agent.registry;

import com.performance.platform.domain.agent.AgentDescriptor;
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
import java.util.function.Consumer;

/**
 * Moniteur de TTL des agents enregistrés.
 * <p>
 * Vérifie périodiquement les agents dont le TTL a expiré
 * ({@code lastHeartbeat + registrationTtl < now}) et appelle
 * {@link TtlTrackable#onAgentExpiredIfStillExpired(AgentId, Instant)}
 * pour chacun — éliminant la race condition TOCTOU.
 * <p>
 * Un callback {@code onExpired} optionnel permet de notifier
 * les couches supérieures (ex: publication d'un event {@code AgentLost}).
 * <p>
 * Utilise un {@link ScheduledExecutorService} sur Virtual Threads.
 */
public class AgentTtlMonitor {

    private static final Logger log = LoggerFactory.getLogger(AgentTtlMonitor.class);

    private final TtlTrackable registry;
    private final long checkIntervalSeconds;
    private final Consumer<AgentId> onExpiredCallback;
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(
            Thread.ofVirtual().factory()
    );

    private final AtomicReference<ScheduledFuture<?>> future = new AtomicReference<>();
    private final AtomicInteger expiredCount = new AtomicInteger(0);

    /**
     * Construit un moniteur de TTL sans callback d'expiration.
     *
     * @param registry             le registre à surveiller (TtlTrackable)
     * @param checkIntervalSeconds l'intervalle entre deux vérifications (≥ 1)
     */
    public AgentTtlMonitor(TtlTrackable registry, long checkIntervalSeconds) {
        this(registry, checkIntervalSeconds, null);
    }

    /**
     * Construit un moniteur de TTL avec un callback appelé pour chaque agent expiré.
     *
     * @param registry             le registre à surveiller (TtlTrackable)
     * @param checkIntervalSeconds l'intervalle entre deux vérifications (≥ 1)
     * @param onExpiredCallback    appelé pour chaque AgentId expiré (peut être null)
     */
    public AgentTtlMonitor(TtlTrackable registry, long checkIntervalSeconds,
                           Consumer<AgentId> onExpiredCallback) {
        this.registry = Objects.requireNonNull(registry, "registry must not be null");
        if (checkIntervalSeconds < 1) {
            throw new IllegalArgumentException("checkIntervalSeconds must be >= 1, got " + checkIntervalSeconds);
        }
        this.checkIntervalSeconds = checkIntervalSeconds;
        this.onExpiredCallback = onExpiredCallback;
    }

    /**
     * Démarre la surveillance périodique des TTL.
     */
    public void start() {
        log.info("action=ttl_monitor_start checkIntervalSeconds={}", checkIntervalSeconds);
        var f = scheduler.scheduleAtFixedRate(
                this::checkExpired,
                checkIntervalSeconds,
                checkIntervalSeconds,
                TimeUnit.SECONDS
        );
        future.set(f);
    }

    /**
     * Arrête la surveillance. Annule les tâches futures et ferme le scheduler.
     */
    public void stop() {
        log.info("action=ttl_monitor_stop totalExpired={}", expiredCount.get());
        var f = future.getAndSet(null);
        if (f != null) {
            f.cancel(false);
        }
        scheduler.shutdown();
    }

    /**
     * Retourne le nombre total d'agents expirés détectés depuis le démarrage.
     */
    public int expiredCount() {
        return expiredCount.get();
    }

    /**
     * Retourne {@code true} si le moniteur est en cours d'exécution.
     */
    public boolean isRunning() {
        var f = future.get();
        return f != null && !f.isDone();
    }

    private void checkExpired() {
        var now = Instant.now();
        var expired = registry.findExpired(now);
        for (AgentDescriptor agent : expired) {
            var agentId = agent.id();
            log.warn("action=agent_expired agentId={} lastHeartbeat={} ttl={}",
                    agentId.value(), agent.lastHeartbeatAt(), agent.registrationTtl());
            registry.onAgentExpiredIfStillExpired(agentId, now);
            expiredCount.incrementAndGet();
            if (onExpiredCallback != null) {
                onExpiredCallback.accept(agentId);
            }
        }
    }
}
