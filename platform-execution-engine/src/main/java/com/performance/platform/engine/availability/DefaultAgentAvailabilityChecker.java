package com.performance.platform.engine.availability;

import com.performance.platform.application.exception.NoAvailableAgentException;
import com.performance.platform.application.ports.out.AgentRegistryPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Implementation de {@link AgentAvailabilityChecker} qui interroge le
 * {@link AgentRegistryPort} avec un polling periodique.
 * <p>
 * Le polling utilise {@link Thread#sleep} qui est compatible Virtual Threads
 * (le carrier thread est libere pendant l'attente).
 * <p>
 * Aucune selection d'agent n'est effectuee — seule la presence d'au moins
 * un agent competent est verifiee, conformement a ADR-008.
 */
@Component
public class DefaultAgentAvailabilityChecker implements AgentAvailabilityChecker {

    private static final Logger log = LoggerFactory.getLogger(DefaultAgentAvailabilityChecker.class);

    /** Intervalle de polling par defaut : 500 ms. */
    private static final long POLL_INTERVAL_MS = 500;

    private final AgentRegistryPort registry;

    public DefaultAgentAvailabilityChecker(AgentRegistryPort registry) {
        this.registry = registry;
    }

    @Override
    public boolean hasAgentFor(String taskName) {
        boolean available = registry.hasAgentFor(taskName);
        log.debug("action=check_agent_availability taskName={} available={}", taskName, available);
        return available;
    }

    @Override
    public void awaitAgentFor(String taskName, Duration timeout) {
        long deadlineMs = System.currentTimeMillis() + timeout.toMillis();

        log.info("action=await_agent_start taskName={} timeoutMs={}", taskName, timeout.toMillis());

        // Verification immediate avant d'entrer dans la boucle de polling
        if (registry.hasAgentFor(taskName)) {
            log.info("action=agent_available_immediate taskName={}", taskName);
            return;
        }

        while (System.currentTimeMillis() < deadlineMs) {
            long remainingMs = deadlineMs - System.currentTimeMillis();
            if (remainingMs <= 0) {
                break;
            }

            long sleepMs = Math.min(POLL_INTERVAL_MS, remainingMs);

            try {
                Thread.sleep(sleepMs);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.warn("action=await_agent_interrupted taskName={}", taskName);
                throw new NoAvailableAgentException(taskName);
            }

            if (registry.hasAgentFor(taskName)) {
                long waitedMs = timeout.toMillis() - (deadlineMs - System.currentTimeMillis());
                log.info("action=agent_available_after_wait taskName={} waitedMs={}", taskName, waitedMs);
                return;
            }
        }

        log.warn("action=no_agent_available taskName={} timeoutMs={}", taskName, timeout.toMillis());
        throw new NoAvailableAgentException(taskName);
    }
}
