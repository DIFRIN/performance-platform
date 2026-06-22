package com.performance.platform.agent.local;

import com.performance.platform.domain.agent.AgentCapabilities;
import com.performance.platform.domain.agent.AgentDescriptor;
import com.performance.platform.domain.agent.AgentState;
import com.performance.platform.domain.id.AgentId;
import com.performance.platform.plugin.TaskExecutor;
import com.performance.platform.transport.inmemory.InMemoryExecutionTransport;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Verifie que le {@link LocalAgent} accepte TOUS les task names
 * derives du registre des {@link TaskExecutor} et ignore
 * {@code AgentProperties.supportedTasks()} (ADR-015).
 */
@DisplayName("LocalAgent — all task names from registry")
class LocalAgentAllTaskNamesTest {

    @Test
    @DisplayName("should return true for all registered task names and false for unknowns")
    void localAgentShouldAcceptAllRegisteredTaskNames() {
        // Given: 3 TaskExecutors enregistres avec Mockito
        var exec1 = mock(TaskExecutor.class);
        when(exec1.getSupportedTaskName()).thenReturn("mock-server");
        var exec2 = mock(TaskExecutor.class);
        when(exec2.getSupportedTaskName()).thenReturn("gatling");
        var exec3 = mock(TaskExecutor.class);
        when(exec3.getSupportedTaskName()).thenReturn("http-client");

        var allTaskNames = Set.of("mock-server", "gatling", "http-client");
        var transport = new InMemoryExecutionTransport();
        var descriptor = new AgentDescriptor(
                AgentId.generate(), "local-agent", "localhost", 8080, null,
                allTaskNames, new AgentCapabilities(10, "1.0.0"),
                AgentState.OFFLINE, Instant.now(), Instant.now(), Duration.ofMinutes(5));

        var agent = new LocalAgent(transport, descriptor,
                Duration.ofMinutes(5), List.of(exec1, exec2, exec3), List.of());

        // Then: canExecute() retourne true pour chaque task name
        assertThat(agent.canExecute("mock-server")).isTrue();
        assertThat(agent.canExecute("gatling")).isTrue();
        assertThat(agent.canExecute("http-client")).isTrue();

        // Et false pour une task non enregistree
        assertThat(agent.canExecute("unknown-task")).isFalse();
    }

    @Test
    @DisplayName("should ignore AgentProperties — derive supportedTaskNames from descriptor only")
    void localAgentShouldIgnoreAgentProperties() {
        // Le LocalAgent derive ses supportedTaskNames du registre,
        // pas de AgentProperties. Ce test confirme que meme avec
        // un supportedTaskNames partiel, canExecute() reflete le descriptor.
        var exec = mock(TaskExecutor.class);
        when(exec.getSupportedTaskName()).thenReturn("mock-server");
        var transport = new InMemoryExecutionTransport();
        var partialNames = Set.of("mock-server"); // seulement 1 sur 2
        var descriptor = new AgentDescriptor(
                AgentId.generate(), "partial-agent", "localhost", 8080, null,
                partialNames, new AgentCapabilities(10, "1.0.0"),
                AgentState.OFFLINE, Instant.now(), Instant.now(), Duration.ofMinutes(5));

        var agent = new LocalAgent(transport, descriptor,
                Duration.ofMinutes(5), List.of(exec), List.of());

        assertThat(agent.canExecute("mock-server")).isTrue();
        assertThat(agent.canExecute("gatling")).isFalse(); // non inclus dans le descriptor
    }
}
