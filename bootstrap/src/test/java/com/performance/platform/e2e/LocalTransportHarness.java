package com.performance.platform.e2e;

import com.performance.platform.agent.filter.DefaultTaskSpecializationFilter;
import com.performance.platform.agent.runtime.TaskAgentRuntime;
import com.performance.platform.application.config.ExecutionConfig;
import com.performance.platform.application.ports.out.ExecutionRepository;
import com.performance.platform.assertion.lifecycle.DefaultLifecycleSignalHandler;
import com.performance.platform.domain.agent.AgentCapabilities;
import com.performance.platform.domain.agent.AgentDescriptor;
import com.performance.platform.domain.agent.AgentState;
import com.performance.platform.domain.id.AgentId;
import com.performance.platform.engine.ExecutionLifecycleDispatcher;
import com.performance.platform.engine.availability.AgentAvailabilityChecker;
import com.performance.platform.engine.correlation.DefaultTaskCorrelationTracker;
import com.performance.platform.engine.correlation.TaskCorrelationTracker;
import com.performance.platform.engine.lifecycle.TransportLifecycleDispatcher;
import com.performance.platform.engine.remote.TransportExecutionEventHandler;
import com.performance.platform.engine.remote.TransportStepDispatcher;
import com.performance.platform.engine.shared.StepDispatcher;
import com.performance.platform.plugin.TaskExecutor;
import com.performance.platform.transport.inmemory.InMemoryExecutionTransport;

import org.springframework.context.ApplicationEventPublisher;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Set;

/**
 * Cablage de test d'une execution complete sur le transport en memoire (ADR-059) :
 * dispatcher, handler d'evenements, tracker de correlation et agent embarque.
 * <p>
 * Remplace l'ancien cablage LOCAL direct ({@code LocalStepDispatcher}) : le
 * moteur emet toujours par les ports de transport, l'agent embarque execute
 * apres avoir recu son grant (ADR-055).
 */
public final class LocalTransportHarness implements AutoCloseable {

    private static final Duration CLAIM_WINDOW = Duration.ofMillis(200);
    private static final Duration AVAILABILITY_TIMEOUT = Duration.ofSeconds(10);
    private static final Duration EXECUTION_TIMEOUT = Duration.ofSeconds(30);

    private final InMemoryExecutionTransport transport = new InMemoryExecutionTransport();
    private final TaskCorrelationTracker tracker = new DefaultTaskCorrelationTracker();
    private final TransportStepDispatcher stepDispatcher;
    private final ExecutionLifecycleDispatcher lifecycleDispatcher;
    private final TaskAgentRuntime agent;
    private boolean started;

    public LocalTransportHarness(ExecutionRepository repository,
                                 ApplicationEventPublisher eventPublisher,
                                 List<TaskExecutor> executors,
                                 Set<String> supportedTaskNames) {
        this.stepDispatcher = new TransportStepDispatcher(
                transport, transport, alwaysAvailable(), tracker,
                new ExecutionConfig(AVAILABILITY_TIMEOUT, EXECUTION_TIMEOUT, CLAIM_WINDOW),
                eventPublisher);
        this.lifecycleDispatcher = new TransportLifecycleDispatcher(transport);
        new TransportExecutionEventHandler(transport, transport, tracker, repository, eventPublisher);

        var descriptor = new AgentDescriptor(
                AgentId.of("local-agent"), "local-agent", "localhost", 0, null,
                Set.copyOf(supportedTaskNames), new AgentCapabilities(10, "1.0.0"),
                AgentState.OFFLINE, Instant.now(), Instant.now(), Duration.ofMinutes(5));
        this.agent = new TaskAgentRuntime(
                transport, transport, transport, transport,
                new DefaultTaskSpecializationFilter(descriptor.supportedTaskNames(), descriptor.id()),
                descriptor,
                EXECUTION_TIMEOUT,
                List.copyOf(executors),
                List.of(),
                null,
                null,
                new DefaultLifecycleSignalHandler(descriptor.supportedTaskNames()::contains));
    }

    /** Demarre l'agent embarque ; le transport est connecte par l'agent. */
    public void start() {
        if (!started) {
            agent.start();
            started = true;
        }
    }

    public StepDispatcher stepDispatcher() {
        return stepDispatcher;
    }

    public ExecutionLifecycleDispatcher lifecycleDispatcher() {
        return lifecycleDispatcher;
    }

    public InMemoryExecutionTransport transport() {
        return transport;
    }

    public TaskAgentRuntime agent() {
        return agent;
    }

    @Override
    public void close() {
        if (started) {
            agent.stop();
            started = false;
        }
        transport.disconnect();
    }

    private static AgentAvailabilityChecker alwaysAvailable() {
        return new AgentAvailabilityChecker() {
            @Override
            public void awaitAgentFor(String taskName, Duration timeout) {
                // l'agent embarque est toujours disponible
            }

            @Override
            public boolean hasAgentFor(String taskName) {
                return true;
            }
        };
    }
}
