package com.performance.platform.agent.e2e;

import com.performance.platform.agent.filter.DefaultTaskSpecializationFilter;
import com.performance.platform.agent.registration.AgentRegistrationPort;
import com.performance.platform.agent.registration.TransportAgentRegistration;
import com.performance.platform.agent.runtime.DistributedAgentRuntime;
import com.performance.platform.domain.agent.AgentCapabilities;
import com.performance.platform.domain.agent.AgentDescriptor;
import com.performance.platform.domain.agent.AgentState;
import com.performance.platform.domain.execution.ExecutionContext;
import com.performance.platform.domain.execution.PartialExecutionContext;
import com.performance.platform.domain.execution.RetryPolicy;
import com.performance.platform.domain.id.AgentId;
import com.performance.platform.domain.id.EventId;
import com.performance.platform.domain.id.ExecutionId;
import com.performance.platform.domain.id.MessageId;
import com.performance.platform.domain.id.ScenarioId;
import com.performance.platform.domain.id.TaskId;
import com.performance.platform.domain.scenario.Phase;
import com.performance.platform.domain.scenario.StepDefinition;
import com.performance.platform.domain.task.TaskResult;
import com.performance.platform.plugin.TaskExecutor;
import com.performance.platform.transport.AgentLifecycleEvent;
import com.performance.platform.transport.inmemory.InMemoryExecutionTransport;
import com.performance.platform.transport.message.ExecutionEvent;
import com.performance.platform.transport.message.TaskExecutionRequest;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * Test E2E de verification du modele configuration-driven pour le mode
 * DISTRIBUTED : config {@code agent.supported-tasks} -> enregistrement ->
 * dispatch -> filtrage -> execution -> resultat.
 *
 * <p>Utilise {@link InMemoryExecutionTransport} (meme abstraction que Kafka,
 * RabbitMQ, etc.) pour valider le flux metier sans couplage a un broker
 * specifique. Le transport Kafka est teste dans
 * {@code platform-transport/KafkaExecutionTransportIT}.
 *
 * <h3>ADR-015 — Configuration-Driven Agent Specialization</h3>
 * <p>Le {@code DistributedAgentRuntime} utilise exclusivement
 * {@code AgentProperties.supportedTasks()} comme source de
 * {@code supportedTaskNames}. Les {@code TaskExecutor} presents dans le
 * registre Spring sont ignores pour le filtrage.
 *
 * <h3>Scenarios testes</h3>
 * <ol>
 *   <li>S1: task supportee -> TaskCompleted</li>
 *   <li>S2: task non-supportee -> pas de TaskClaimedByAgent</li>
 *   <li>S3: agent idle (config vide) -> pas d'execution</li>
 *   <li>S4: agent multi-task -> execution de la deuxieme task</li>
 *   <li>S5: AgentRegistered.supportedTaskNames = config (pas registre)</li>
 * </ol>
 */
@DisplayName("Agent Config-to-Execution E2E")
@Tag("integration")
class AgentConfigToExecutionE2ETest {

    private InMemoryExecutionTransport transport;

    @BeforeEach
    void setUp() {
        transport = new InMemoryExecutionTransport();
        transport.connect();
    }

    @AfterEach
    void tearDown() {
        if (transport != null && transport.isConnected()) {
            transport.disconnect();
        }
    }

    // ========================================================================
    // Scenario S1 : task supportee executee
    // ========================================================================

    @Nested
    @DisplayName("E2E-117-S1: Task supportee executee avec succes")
    class TaskSupported {

        @Test
        @DisplayName("Should execute supported task and produce TaskCompleted event")
        @Timeout(value = 30)
        void shouldExecuteSupportedTask() {
            // Given: agent avec supported-tasks=[mock-server]
            var configuredNames = Set.of("mock-server");
            var agentId = AgentId.generate();

            var executor = buildSuccessExecutor("mock-server");
            var agent = buildAgent(configuredNames, agentId, List.of(executor));

            var lifecycleEvents = new CopyOnWriteArrayList<AgentLifecycleEvent>();
            transport.subscribeAgentEvents(lifecycleEvents::add);
            var executionEvents = new CopyOnWriteArrayList<ExecutionEvent>();
            transport.subscribe(executionEvents::add);

            // When: agent demarre
            agent.start();

            // Then: AgentRegistered avec supportedTaskNames = config
            await().atMost(15, TimeUnit.SECONDS).until(() ->
                    lifecycleEvents.stream().anyMatch(e ->
                            AgentLifecycleEvent.AGENT_REGISTERED.equals(e.eventType())));

            var registeredEvent = lifecycleEvents.stream()
                    .filter(e -> AgentLifecycleEvent.AGENT_REGISTERED.equals(e.eventType()))
                    .findFirst().orElseThrow();
            @SuppressWarnings("unchecked")
            var supportedFromEvent = (Set<String>) registeredEvent.payload()
                    .get("supportedTaskNames");
            assertThat(supportedFromEvent).containsExactlyInAnyOrder("mock-server");

            // When: dispatch task mock-server
            var executionId = ExecutionId.generate();
            transport.dispatchTask(buildTaskRequest("mock-server", executionId));

            // Then: TaskCompleted event
            await().atMost(15, TimeUnit.SECONDS).until(() ->
                    executionEvents.stream().anyMatch(e ->
                            ExecutionEvent.TASK_COMPLETED.equals(e.eventType())));

            var claimed = executionEvents.stream()
                    .filter(e -> ExecutionEvent.TASK_CLAIMED.equals(e.eventType()))
                    .count();
            assertThat(claimed).isPositive();

            var completed = executionEvents.stream()
                    .filter(e -> ExecutionEvent.TASK_COMPLETED.equals(e.eventType()))
                    .findFirst().orElseThrow();
            assertThat(completed.payload().get("status")).isEqualTo("SUCCESS");

            agent.stop();
        }
    }

    // ========================================================================
    // Scenario S2 : task non-supportee ignoree
    // ========================================================================

    @Nested
    @DisplayName("E2E-117-S2: Task non-supportee ignoree (NOT_RESPONSIBLE)")
    class TaskUnsupported {

        @Test
        @DisplayName("Should NOT claim or execute task not in configured task names")
        @Timeout(value = 30)
        void shouldIgnoreUnsupportedTask() {
            // Given: agent config [mock-server] only
            var configuredNames = Set.of("mock-server");
            var agentId = AgentId.generate();
            var agent = buildAgent(configuredNames, agentId, List.of());

            var executionEvents = new CopyOnWriteArrayList<ExecutionEvent>();
            transport.subscribe(executionEvents::add);

            agent.start();
            await().atMost(10, TimeUnit.SECONDS).until(() ->
                    agent.getState() == AgentState.IDLE);

            // When: dispatch task non-supportee (gatling)
            transport.dispatchTask(buildTaskRequest("gatling", ExecutionId.generate()));

            // Then: aucun TaskClaimedByAgent
            await().pollDelay(1, TimeUnit.SECONDS).until(() -> true);
            long claimCount = executionEvents.stream()
                    .filter(e -> ExecutionEvent.TASK_CLAIMED.equals(e.eventType()))
                    .count();
            assertThat(claimCount).isZero();

            agent.stop();
        }
    }

    // ========================================================================
    // Scenario S3 : agent idle (config vide)
    // ========================================================================

    @Nested
    @DisplayName("E2E-117-S3: Agent idle avec supported-tasks vide")
    class AgentIdle {

        @Test
        @DisplayName("Should not execute any task when supported-tasks is empty")
        @Timeout(value = 30)
        void shouldNotExecuteWhenEmptyConfig() {
            // Given: agent avec supported-tasks=[]
            var configuredNames = Set.<String>of();
            var agentId = AgentId.generate();
            var agent = buildAgent(configuredNames, agentId, List.of());

            var executionEvents = new CopyOnWriteArrayList<ExecutionEvent>();
            transport.subscribe(executionEvents::add);

            agent.start();
            await().atMost(10, TimeUnit.SECONDS).until(() ->
                    agent.getState() == AgentState.IDLE);

            // When: dispatch task mock-server
            transport.dispatchTask(buildTaskRequest("mock-server", ExecutionId.generate()));

            // Then: aucun claim
            await().pollDelay(1, TimeUnit.SECONDS).until(() -> true);
            long claimCount = executionEvents.stream()
                    .filter(e -> ExecutionEvent.TASK_CLAIMED.equals(e.eventType()))
                    .count();
            assertThat(claimCount).isZero();

            // L'agent doit exposer 0 supportedTaskNames
            assertThat(agent.getDescriptor().supportedTaskNames()).isEmpty();

            agent.stop();
        }
    }

    // ========================================================================
    // Scenario S4 : agent multi-task
    // ========================================================================

    @Nested
    @DisplayName("E2E-117-S4: Agent multi-task config")
    class MultiTask {

        @Test
        @DisplayName("Should execute second configured task among multiple")
        @Timeout(value = 30)
        void shouldExecuteConfiguredTaskAmongMultiple() {
            // Given: agent config [mock-server, http-client]
            var configuredNames = Set.of("mock-server", "http-client");
            var agentId = AgentId.generate();

            var executor = buildSuccessExecutor("http-client");
            var agent = buildAgent(configuredNames, agentId, List.of(executor));

            var executionEvents = new CopyOnWriteArrayList<ExecutionEvent>();
            transport.subscribe(executionEvents::add);

            agent.start();
            await().atMost(10, TimeUnit.SECONDS).until(() ->
                    agent.getState() == AgentState.IDLE);

            // When: dispatch http-client (la deuxieme task de la config)
            transport.dispatchTask(buildTaskRequest("http-client", ExecutionId.generate()));

            // Then: TaskCompleted
            await().atMost(15, TimeUnit.SECONDS).until(() ->
                    executionEvents.stream().anyMatch(e ->
                            ExecutionEvent.TASK_COMPLETED.equals(e.eventType())));

            var completed = executionEvents.stream()
                    .filter(e -> ExecutionEvent.TASK_COMPLETED.equals(e.eventType()))
                    .findFirst().orElseThrow();
            assertThat(completed.payload().get("status")).isEqualTo("SUCCESS");

            agent.stop();
        }
    }

    // ========================================================================
    // Scenario S5 : enregistrement avec supportedTaskNames exacts
    // ========================================================================

    @Nested
    @DisplayName("E2E-117-S5: AgentRegistered.supportedTaskNames = config YAML")
    class RegistrationSupportedTaskNames {

        @Test
        @DisplayName("AgentRegistered payload must contain configured names, NOT registry names")
        @Timeout(value = 30)
        void registeredEventShouldContainOnlyConfiguredTaskNames() {
            // Given: 3 executors dans le registre, 2 dans la config,
            //        "gatling" present dans le registre mais PAS dans la config
            var exec1 = buildSuccessExecutor("mock-server");
            var exec2 = buildSuccessExecutor("gatling"); // Dans le registre, pas dans la config
            var exec3 = buildSuccessExecutor("http-client");

            var configuredNames = Set.of("mock-server", "http-client");
            var agentId = AgentId.generate();

            var agent = buildAgent(configuredNames, agentId,
                    List.of(exec1, exec2, exec3));

            var lifecycleEvents = new CopyOnWriteArrayList<AgentLifecycleEvent>();
            transport.subscribeAgentEvents(lifecycleEvents::add);

            // When: agent demarre
            agent.start();
            await().atMost(10, TimeUnit.SECONDS).until(() ->
                    agent.getState() == AgentState.IDLE);

            // Then: AgentRegistered contient exactement les noms configures
            await().atMost(10, TimeUnit.SECONDS).until(() ->
                    lifecycleEvents.stream().anyMatch(e ->
                            AgentLifecycleEvent.AGENT_REGISTERED.equals(e.eventType())));

            var registeredEvent = lifecycleEvents.stream()
                    .filter(e -> AgentLifecycleEvent.AGENT_REGISTERED.equals(e.eventType()))
                    .findFirst().orElseThrow();

            @SuppressWarnings("unchecked")
            var supportedFromEvent = (Set<String>) registeredEvent.payload()
                    .get("supportedTaskNames");
            assertThat(supportedFromEvent)
                    .containsExactlyInAnyOrder("mock-server", "http-client");
            assertThat(supportedFromEvent)
                    .doesNotContain("gatling"); // Executor present dans le registre mais pas dans la config

            // Cross-check: le descriptor expose la meme chose
            assertThat(agent.getDescriptor().supportedTaskNames())
                    .containsExactlyInAnyOrder("mock-server", "http-client");
            assertThat(agent.getDescriptor().supportedTaskNames())
                    .doesNotContain("gatling");

            agent.stop();
        }
    }

    // ========================================================================
    // Helpers
    // ========================================================================

    private TaskExecutor buildSuccessExecutor(String taskName) {
        return new TaskExecutor() {
            @Override
            public TaskResult execute(ExecutionContext ctx, StepDefinition step) {
                return TaskResult.success(
                        step.id(), taskName, Duration.ofMillis(5),
                        Map.of("status", "SUCCESS"));
            }

            @Override
            public String getSupportedTaskName() {
                return taskName;
            }
        };
    }

    private DistributedAgentRuntime buildAgent(
            Set<String> configuredNames,
            AgentId agentId,
            List<TaskExecutor> executors) {

        var filter = new DefaultTaskSpecializationFilter(configuredNames, agentId);
        var registrationPort = new TransportAgentRegistration(transport);

        var descriptor = new AgentDescriptor(
                agentId,
                "e2e-agent-" + agentId.value().substring(0, 8),
                "localhost",
                9090,
                "http://localhost:9090/callback",
                configuredNames,
                new AgentCapabilities(0, "1.0.0"),
                AgentState.OFFLINE,
                Instant.now(),
                Instant.now(),
                Duration.ofMinutes(5)
        );

        return new DistributedAgentRuntime(
                transport,
                filter,
                registrationPort,
                descriptor,
                Duration.ofSeconds(5),
                Duration.ofMinutes(5),
                executors,
                List.of()
        );
    }

    private TaskExecutionRequest buildTaskRequest(String taskName, ExecutionId executionId) {
        var step = new StepDefinition(
                TaskId.of("task-" + UUID.randomUUID().toString().substring(0, 8)),
                taskName, Phase.INJECTION, Map.of(),
                List.of(), List.of(), Duration.ofSeconds(30), null);
        return new TaskExecutionRequest(
                MessageId.generate(), executionId, step,
                PartialExecutionContext.empty(executionId, ScenarioId.of("test-scenario")),
                Instant.now(), RetryPolicy.defaults());
    }
}
