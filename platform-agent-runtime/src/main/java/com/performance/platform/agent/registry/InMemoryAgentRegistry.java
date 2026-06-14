package com.performance.platform.agent.registry;

import com.performance.platform.application.ports.out.AgentRegistryPort;
import com.performance.platform.domain.agent.AgentDescriptor;
import com.performance.platform.domain.agent.AgentHeartbeat;
import com.performance.platform.domain.id.AgentId;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Implémentation en mémoire de {@link AgentRegistry}.
 * <p>
 * Stocke les agents dans une {@link ConcurrentHashMap} thread-safe.
 * Le heartbeat met à jour le timestamp et l'état de l'agent dans le
 * descripteur. L'expiration et le désenregistrement retirent l'agent
 * du registre.
 * <p>
 * {@code findByTaskName} retourne TOUS les agents dont
 * {@code supportedTaskNames} contient le nom — pas de sélection
 * (voir ADR-008).
 * <p>
 * Le câblage Spring ({@code @Component}, {@code @ConditionalOnProperty})
 * sera ajouté dans {@code TransportConfiguration} ou équivalent lors de
 * l'assemblage applicatif (ISSUE-077, PDR-018).
 */
public class InMemoryAgentRegistry implements AgentRegistry, TtlTrackable {

    private static final Logger log = LoggerFactory.getLogger(InMemoryAgentRegistry.class);

    private final ConcurrentMap<AgentId, AgentDescriptor> agents = new ConcurrentHashMap<>();

    @Override
    public void onAgentRegistered(AgentDescriptor descriptor) {
        Objects.requireNonNull(descriptor, "descriptor must not be null");
        agents.put(descriptor.id(), descriptor);
        log.info("action=agent_registered agentId={} name={} supportedTasks={} agentCount={}",
                descriptor.id().value(), descriptor.name(),
                descriptor.supportedTaskNames().size(), agents.size());
    }

    @Override
    public void onAgentHeartbeat(AgentId agentId, AgentHeartbeat heartbeat) {
        Objects.requireNonNull(agentId, "agentId must not be null");
        Objects.requireNonNull(heartbeat, "heartbeat must not be null");
        agents.computeIfPresent(agentId, (id, existing) ->
                new AgentDescriptor(
                        existing.id(),
                        existing.name(),
                        existing.host(),
                        existing.port(),
                        existing.httpCallbackUrl(),
                        existing.supportedTaskNames(),
                        existing.capabilities(),
                        heartbeat.state(),
                        existing.registeredAt(),
                        heartbeat.sentAt(),
                        existing.registrationTtl()
                ));
        log.debug("action=heartbeat_received agentId={} state={} activeTasks={}",
                agentId.value(), heartbeat.state(), heartbeat.activeTasks());
    }

    @Override
    public void onAgentExpired(AgentId agentId) {
        Objects.requireNonNull(agentId, "agentId must not be null");
        agents.remove(agentId);
        log.info("action=agent_expired agentId={} agentCount={}", agentId.value(), agents.size());
    }

    @Override
    public void onAgentDeregistered(AgentId agentId) {
        Objects.requireNonNull(agentId, "agentId must not be null");
        agents.remove(agentId);
        log.info("action=agent_deregistered agentId={} agentCount={}", agentId.value(), agents.size());
    }

    @Override
    public List<AgentDescriptor> findByTaskName(String taskName) {
        Objects.requireNonNull(taskName, "taskName must not be null");
        return agents.values().stream()
                .filter(d -> d.supportedTaskNames().contains(taskName))
                .toList();
    }

    @Override
    public boolean hasAgentFor(String taskName) {
        Objects.requireNonNull(taskName, "taskName must not be null");
        return agents.values().stream()
                .anyMatch(d -> d.supportedTaskNames().contains(taskName));
    }

    @Override
    public Optional<AgentDescriptor> findById(AgentId agentId) {
        Objects.requireNonNull(agentId, "agentId must not be null");
        return Optional.ofNullable(agents.get(agentId));
    }

    @Override
    public List<AgentDescriptor> findAll() {
        return List.copyOf(agents.values());
    }

    /**
     * Retourne les agents dont le TTL a expiré (dernier heartbeat + TTL < now).
     */
    @Override
    public List<AgentDescriptor> findExpired(Instant now) {
        return agents.values().stream()
                .filter(d -> isExpired(d, now))
                .toList();
    }

    /**
     * Supprime atomiquement un agent SI son TTL est toujours expiré.
     * Utilise {@code computeIfPresent} pour éviter la race TOCTOU :
     * si un heartbeat rafraîchit l'agent entre {@code findExpired()} et
     * l'appel à cette méthode, l'agent n'est PAS supprimé.
     */
    @Override
    public void onAgentExpiredIfStillExpired(AgentId agentId, Instant checkedAt) {
        agents.computeIfPresent(agentId, (id, existing) ->
                isExpired(existing, checkedAt) ? null : existing);
    }

    /**
     * Retourne le nombre d'agents enregistrés (pour les tests).
     */
    int agentCount() {
        return agents.size();
    }

    private static boolean isExpired(AgentDescriptor d, Instant now) {
        var ttl = d.registrationTtl();
        var deadline = d.lastHeartbeatAt().plus(ttl);
        return now.isAfter(deadline);
    }
}
