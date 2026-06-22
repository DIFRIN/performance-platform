package com.performance.platform.agent.runtime;

import com.performance.platform.agent.filter.DefaultTaskSpecializationFilter;
import com.performance.platform.agent.filter.TaskFilterResult;
import com.performance.platform.agent.registration.AgentRegistrationPort;
import com.performance.platform.domain.agent.AgentCapabilities;
import com.performance.platform.domain.agent.AgentDescriptor;
import com.performance.platform.domain.agent.AgentState;
import com.performance.platform.domain.execution.PartialExecutionContext;
import com.performance.platform.domain.execution.RetryPolicy;
import com.performance.platform.domain.id.AgentId;
import com.performance.platform.domain.id.ExecutionId;
import com.performance.platform.domain.id.MessageId;
import com.performance.platform.domain.id.ScenarioId;
import com.performance.platform.domain.id.TaskId;
import com.performance.platform.domain.scenario.Phase;
import com.performance.platform.domain.scenario.StepDefinition;
import com.performance.platform.plugin.TaskExecutor;
import com.performance.platform.transport.ExecutionTransport;
import com.performance.platform.transport.message.TaskExecutionRequest;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Verifie que le {@link DistributedAgentRuntime} utilise exclusivement
 * les {@code supportedTaskNames} de son {@link AgentDescriptor}
 * et non les annotations ou le registre des {@link TaskExecutor} (ADR-015).
 *
 * <p>Les 3 executors dans le registre (mock-server, gatling, http-client)
 * sont TOUS charges par le PluginLoader mais SEULS ceux listes dans la
 * configuration {@code agent.supported-tasks} sont actives.</p>
 */
@DisplayName("DistributedAgentRuntime — config-driven supportedTaskNames")
class DistributedAgentConfigDrivenTest {

    @Test
    @DisplayName("should only use configured task names, ignoring executors in registry")
    void distributedAgentShouldOnlyUseConfiguredTaskNames() {
        // Given: 3 executors dans le registre, mais seulement 2 dans la config
        var exec1 = mock(TaskExecutor.class);
        when(exec1.getSupportedTaskName()).thenReturn("mock-server");
        var exec2 = mock(TaskExecutor.class);
        when(exec2.getSupportedTaskName()).thenReturn("gatling");
        var exec3 = mock(TaskExecutor.class);
        when(exec3.getSupportedTaskName()).thenReturn("http-client");

        var configuredNames = Set.of("mock-server", "http-client"); // gatling PAS inclus
        var filter = new DefaultTaskSpecializationFilter(configuredNames, AgentId.generate());
        var transport = mock(ExecutionTransport.class);
        var registrationPort = mock(AgentRegistrationPort.class);

        var descriptor = new AgentDescriptor(
                AgentId.generate(), "test-agent", "localhost", 8080, null,
                configuredNames, new AgentCapabilities(10, "1.0.0"),
                AgentState.OFFLINE, Instant.now(), Instant.now(), Duration.ofMinutes(5));

        var agent = new DistributedAgentRuntime(
                transport, filter, registrationPort, descriptor,
                Duration.ofSeconds(10), Duration.ofMinutes(5),
                List.of(exec1, exec2, exec3), List.of());

        // Then: canExecute() reflete uniquement la config
        assertThat(agent.canExecute("mock-server")).isTrue();
        assertThat(agent.canExecute("http-client")).isTrue();
        assertThat(agent.canExecute("gatling")).isFalse(); // pas dans la config
    }

    @Test
    @DisplayName("filter should reject task not in configured names")
    void filterShouldRejectTaskNotInConfiguredNames() {
        var configuredNames = Set.of("mock-server");
        var filter = new DefaultTaskSpecializationFilter(configuredNames, AgentId.generate());

        // Step avec taskName "gatling" -> pas dans la config
        var stepDef = new StepDefinition(
                TaskId.of("step-gatling"),
                "gatling",
                Phase.INJECTION,
                Map.of(),
                List.of(),
                List.of(),
                Duration.ofSeconds(30),
                RetryPolicy.defaults());

        var executionId = ExecutionId.generate();
        var scenarioId = ScenarioId.of("test-scenario");
        var request = new TaskExecutionRequest(
                MessageId.generate(),
                executionId,
                stepDef,
                new PartialExecutionContext(executionId, scenarioId, Map.of()),
                Instant.now(),
                RetryPolicy.defaults());

        var result = filter.filter(request);
        assertThat(result).isInstanceOf(TaskFilterResult.NotResponsible.class);
    }

    @Test
    @DisplayName("descriptor should expose only configured task names, not all registry names")
    void descriptorShouldExposeOnlyConfiguredTaskNames() {
        // Given: 3 executors dans le registre, mais seulement 2 configures
        var exec1 = mock(TaskExecutor.class);
        when(exec1.getSupportedTaskName()).thenReturn("mock-server");
        var exec2 = mock(TaskExecutor.class);
        when(exec2.getSupportedTaskName()).thenReturn("gatling");
        var exec3 = mock(TaskExecutor.class);
        when(exec3.getSupportedTaskName()).thenReturn("http-client");

        var configuredNames = Set.of("mock-server", "http-client");
        var filter = new DefaultTaskSpecializationFilter(configuredNames, AgentId.generate());
        var transport = mock(ExecutionTransport.class);
        var registrationPort = mock(AgentRegistrationPort.class);

        var descriptor = new AgentDescriptor(
                AgentId.generate(), "test-agent", "localhost", 8080, null,
                configuredNames, new AgentCapabilities(10, "1.0.0"),
                AgentState.OFFLINE, Instant.now(), Instant.now(), Duration.ofMinutes(5));

        var agent = new DistributedAgentRuntime(
                transport, filter, registrationPort, descriptor,
                Duration.ofSeconds(10), Duration.ofMinutes(5),
                List.of(exec1, exec2, exec3), List.of());

        // Then: getDescriptor() expose seulement les noms configures
        var actualDescriptor = agent.getDescriptor();
        assertThat(actualDescriptor.supportedTaskNames())
                .containsExactlyInAnyOrder("mock-server", "http-client");
        assertThat(actualDescriptor.supportedTaskNames())
                .doesNotContain("gatling");
    }

    @Test
    @DisplayName("agent with empty supported task names should not execute any task")
    void agentWithEmptySupportedTaskNamesShouldNotExecuteAnyTask() {
        var emptyNames = Set.<String>of();
        var filter = new DefaultTaskSpecializationFilter(emptyNames, AgentId.generate());
        var transport = mock(ExecutionTransport.class);
        var registrationPort = mock(AgentRegistrationPort.class);

        var descriptor = new AgentDescriptor(
                AgentId.generate(), "idle-agent", "localhost", 8080, null,
                emptyNames, new AgentCapabilities(0, "1.0.0"),
                AgentState.OFFLINE, Instant.now(), Instant.now(), Duration.ofMinutes(5));

        var agent = new DistributedAgentRuntime(
                transport, filter, registrationPort, descriptor,
                Duration.ofSeconds(10), Duration.ofMinutes(5),
                List.of(), List.of());

        // Agent idle : aucune task acceptee
        assertThat(agent.canExecute("mock-server")).isFalse();
        assertThat(agent.canExecute("gatling")).isFalse();
        assertThat(agent.canExecute("http-client")).isFalse();
        assertThat(agent.getDescriptor().supportedTaskNames()).isEmpty();
    }
}
