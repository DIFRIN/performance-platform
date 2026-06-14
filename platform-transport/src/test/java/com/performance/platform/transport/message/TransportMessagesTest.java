package com.performance.platform.transport.message;

import com.performance.platform.domain.execution.PartialExecutionContext;
import com.performance.platform.domain.execution.RetryPolicy;
import com.performance.platform.domain.id.*;
import com.performance.platform.domain.scenario.Phase;
import com.performance.platform.domain.scenario.StepDefinition;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("TransportMessages — TaskExecutionRequest + ExecutionEvent")
class TransportMessagesTest {

    // === Fixtures ===

    private static final ExecutionId EXECUTION_ID = ExecutionId.generate();
    private static final ScenarioId SCENARIO_ID = ScenarioId.of("scenario-1");
    private static final TaskId TASK_ID = TaskId.of("task-1");
    private static final MessageId MESSAGE_ID = MessageId.generate();
    private static final EventId EVENT_ID = EventId.generate();
    private static final AgentId AGENT_ID = AgentId.generate();

    private static StepDefinition sampleStep() {
        return new StepDefinition(
                TASK_ID,
                "sample-task",
                Phase.INJECTION,
                Map.of("key", "value"),
                List.of(),
                List.of(),
                Duration.ofSeconds(30),
                new RetryPolicy(
                        3,
                        Duration.ofMillis(100),
                        2.0,
                        Duration.ofSeconds(5),
                        Set.of(RuntimeException.class)
                )
        );
    }

    private static PartialExecutionContext sampleContext() {
        return new PartialExecutionContext(
                EXECUTION_ID,
                SCENARIO_ID,
                Map.of("task-0", Map.of("agent-1", "result"))
        );
    }

    // ==================== TaskExecutionRequest ====================

    @Nested
    @DisplayName("TaskExecutionRequest")
    class TaskExecutionRequestTests {

        @Test
        @DisplayName("should create with all fields")
        void shouldCreate() {
            var request = new TaskExecutionRequest(
                    MESSAGE_ID,
                    EXECUTION_ID,
                    sampleStep(),
                    sampleContext(),
                    Instant.now(),
                    new RetryPolicy(3, Duration.ofMillis(100), 2.0,
                            Duration.ofSeconds(5), Set.of(RuntimeException.class))
            );

            assertThat(request.id()).isEqualTo(MESSAGE_ID);
            assertThat(request.executionId()).isEqualTo(EXECUTION_ID);
            assertThat(request.step()).isNotNull();
            assertThat(request.context()).isNotNull();
            assertThat(request.dispatchedAt()).isNotNull();
            assertThat(request.retryPolicy()).isNotNull();
        }

        @Test
        @DisplayName("should reject null id")
        void shouldRejectNullId() {
            assertThatThrownBy(() -> new TaskExecutionRequest(
                    null, EXECUTION_ID, sampleStep(), sampleContext(),
                    Instant.now(),
                    new RetryPolicy(3, Duration.ofMillis(100), 2.0,
                            Duration.ofSeconds(5), Set.of(RuntimeException.class))))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("id");
        }

        @Test
        @DisplayName("should reject null executionId")
        void shouldRejectNullExecutionId() {
            assertThatThrownBy(() -> new TaskExecutionRequest(
                    MESSAGE_ID, null, sampleStep(), sampleContext(),
                    Instant.now(),
                    new RetryPolicy(3, Duration.ofMillis(100), 2.0,
                            Duration.ofSeconds(5), Set.of(RuntimeException.class))))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("executionId");
        }

        @Test
        @DisplayName("should reject null step")
        void shouldRejectNullStep() {
            assertThatThrownBy(() -> new TaskExecutionRequest(
                    MESSAGE_ID, EXECUTION_ID, null, sampleContext(),
                    Instant.now(),
                    new RetryPolicy(3, Duration.ofMillis(100), 2.0,
                            Duration.ofSeconds(5), Set.of(RuntimeException.class))))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("step");
        }

        @Test
        @DisplayName("should reject null context")
        void shouldRejectNullContext() {
            assertThatThrownBy(() -> new TaskExecutionRequest(
                    MESSAGE_ID, EXECUTION_ID, sampleStep(), null,
                    Instant.now(),
                    new RetryPolicy(3, Duration.ofMillis(100), 2.0,
                            Duration.ofSeconds(5), Set.of(RuntimeException.class))))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("context");
        }

        @Test
        @DisplayName("should reject null dispatchedAt")
        void shouldRejectNullDispatchedAt() {
            assertThatThrownBy(() -> new TaskExecutionRequest(
                    MESSAGE_ID, EXECUTION_ID, sampleStep(), sampleContext(),
                    null,
                    new RetryPolicy(3, Duration.ofMillis(100), 2.0,
                            Duration.ofSeconds(5), Set.of(RuntimeException.class))))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("dispatchedAt");
        }

        @Test
        @DisplayName("should reject null retryPolicy")
        void shouldRejectNullRetryPolicy() {
            assertThatThrownBy(() -> new TaskExecutionRequest(
                    MESSAGE_ID, EXECUTION_ID, sampleStep(), sampleContext(),
                    Instant.now(), null))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("retryPolicy");
        }

        @Test
        @DisplayName("should not contain targetAgentId field")
        void shouldNotContainTargetAgentId() {
            // ISSUE-026: TaskExecutionRequest uses broadcast — no targetAgentId.
            // Verify by reflection that the record components do not include
            // any targetAgentId concept.
            var components = TaskExecutionRequest.class.getRecordComponents();
            assertThat(components)
                    .noneMatch(c -> c.getName().contains("targetAgent"));
        }
    }

    // ==================== ExecutionEvent ====================

    @Nested
    @DisplayName("ExecutionEvent — instanciation")
    class ExecutionEventInstanciationTests {

        @Test
        @DisplayName("should create with all fields")
        void shouldCreate() {
            var event = new ExecutionEvent(
                    EVENT_ID, EXECUTION_ID, MESSAGE_ID, AGENT_ID,
                    ExecutionEvent.TASK_COMPLETED,
                    Map.of("status", "SUCCESS"),
                    Instant.now());

            assertThat(event.id()).isEqualTo(EVENT_ID);
            assertThat(event.executionId()).isEqualTo(EXECUTION_ID);
            assertThat(event.correlationId()).isEqualTo(MESSAGE_ID);
            assertThat(event.agentId()).isEqualTo(AGENT_ID);
            assertThat(event.eventType()).isEqualTo(ExecutionEvent.TASK_COMPLETED);
            assertThat(event.payload()).containsEntry("status", "SUCCESS");
            assertThat(event.occurredAt()).isNotNull();
        }

        @Test
        @DisplayName("should allow nullable correlationId")
        void shouldAllowNullableCorrelationId() {
            var event = new ExecutionEvent(
                    EVENT_ID, EXECUTION_ID, null, AGENT_ID,
                    ExecutionEvent.AGENT_REGISTERED,
                    Map.of(), Instant.now());
            assertThat(event.correlationId()).isNull();
        }

        @Test
        @DisplayName("should allow nullable agentId")
        void shouldAllowNullableAgentId() {
            var event = new ExecutionEvent(
                    EVENT_ID, EXECUTION_ID, MESSAGE_ID, null,
                    ExecutionEvent.TASK_FAILED,
                    Map.of(), Instant.now());
            assertThat(event.agentId()).isNull();
        }

        @Test
        @DisplayName("should defensively copy payload")
        void shouldDefensivelyCopyPayload() {
            var mutablePayload = new HashMap<String, Object>();
            mutablePayload.put("key", "value");
            var event = new ExecutionEvent(
                    EVENT_ID, EXECUTION_ID, MESSAGE_ID, AGENT_ID,
                    ExecutionEvent.TASK_COMPLETED,
                    mutablePayload, Instant.now());

            mutablePayload.put("extra", "should not appear");

            assertThat(event.payload()).hasSize(1);
            assertThat(event.payload()).containsEntry("key", "value");
        }

        @Test
        @DisplayName("should treat null payload as empty map")
        void shouldTreatNullPayloadAsEmptyMap() {
            var event = new ExecutionEvent(
                    EVENT_ID, EXECUTION_ID, null, null,
                    ExecutionEvent.TASK_FAILED,
                    null, Instant.now());
            assertThat(event.payload()).isEmpty();
        }

        @Test
        @DisplayName("should reject null id")
        void shouldRejectNullId() {
            assertThatThrownBy(() -> new ExecutionEvent(
                    null, EXECUTION_ID, MESSAGE_ID, AGENT_ID,
                    ExecutionEvent.TASK_COMPLETED, Map.of(), Instant.now()))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("id");
        }

        @Test
        @DisplayName("should reject null executionId")
        void shouldRejectNullExecutionId() {
            assertThatThrownBy(() -> new ExecutionEvent(
                    EVENT_ID, null, MESSAGE_ID, AGENT_ID,
                    ExecutionEvent.TASK_COMPLETED, Map.of(), Instant.now()))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("executionId");
        }

        @Test
        @DisplayName("should reject null eventType")
        void shouldRejectNullEventType() {
            assertThatThrownBy(() -> new ExecutionEvent(
                    EVENT_ID, EXECUTION_ID, MESSAGE_ID, AGENT_ID,
                    null, Map.of(), Instant.now()))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("eventType");
        }

        @Test
        @DisplayName("should reject null occurredAt")
        void shouldRejectNullOccurredAt() {
            assertThatThrownBy(() -> new ExecutionEvent(
                    EVENT_ID, EXECUTION_ID, MESSAGE_ID, AGENT_ID,
                    ExecutionEvent.TASK_COMPLETED, Map.of(), null))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("occurredAt");
        }
    }

    @Nested
    @DisplayName("ExecutionEvent — constantes")
    class ExecutionEventConstantesTests {

        @Test
        @DisplayName("AGENT_REGISTERED = \"AgentRegistered\"")
        void agentRegistered() {
            assertThat(ExecutionEvent.AGENT_REGISTERED).isEqualTo("AgentRegistered");
        }

        @Test
        @DisplayName("AGENT_HEARTBEAT = \"AgentHeartbeat\"")
        void agentHeartbeat() {
            assertThat(ExecutionEvent.AGENT_HEARTBEAT).isEqualTo("AgentHeartbeat");
        }

        @Test
        @DisplayName("AGENT_DEREGISTERED = \"AgentDeregistered\"")
        void agentDeregistered() {
            assertThat(ExecutionEvent.AGENT_DEREGISTERED).isEqualTo("AgentDeregistered");
        }

        @Test
        @DisplayName("TASK_CLAIMED = \"TaskClaimedByAgent\"")
        void taskClaimed() {
            assertThat(ExecutionEvent.TASK_CLAIMED).isEqualTo("TaskClaimedByAgent");
        }

        @Test
        @DisplayName("TASK_WORK_IN_PROGRESS = \"TaskWorkInProgress\"")
        void taskWorkInProgress() {
            assertThat(ExecutionEvent.TASK_WORK_IN_PROGRESS).isEqualTo("TaskWorkInProgress");
        }

        @Test
        @DisplayName("TASK_COMPLETED = \"TaskCompleted\"")
        void taskCompleted() {
            assertThat(ExecutionEvent.TASK_COMPLETED).isEqualTo("TaskCompleted");
        }

        @Test
        @DisplayName("TASK_FAILED = \"TaskFailed\"")
        void taskFailed() {
            assertThat(ExecutionEvent.TASK_FAILED).isEqualTo("TaskFailed");
        }
    }

    @Nested
    @DisplayName("ExecutionEvent — of() factory")
    class ExecutionEventOfFactoryTests {

        @Test
        @DisplayName("should create identical event via of()")
        void shouldCreateIdenticalEvent() {
            Map<String, Object> payload = Map.of("key", (Object) "value");
            var instant = Instant.now();

            var direct = new ExecutionEvent(
                    EVENT_ID, EXECUTION_ID, MESSAGE_ID, AGENT_ID,
                    ExecutionEvent.TASK_COMPLETED, payload, instant);
            var viaFactory = ExecutionEvent.of(
                    EVENT_ID, EXECUTION_ID, MESSAGE_ID, AGENT_ID,
                    ExecutionEvent.TASK_COMPLETED, payload, instant);

            assertThat(viaFactory.id()).isEqualTo(direct.id());
            assertThat(viaFactory.executionId()).isEqualTo(direct.executionId());
            assertThat(viaFactory.correlationId()).isEqualTo(direct.correlationId());
            assertThat(viaFactory.agentId()).isEqualTo(direct.agentId());
            assertThat(viaFactory.eventType()).isEqualTo(direct.eventType());
            assertThat(viaFactory.payload()).isEqualTo(direct.payload());
            assertThat(viaFactory.occurredAt()).isEqualTo(direct.occurredAt());
        }

        @Test
        @DisplayName("of() should still defensively copy payload")
        void ofShouldDefensivelyCopyPayload() {
            var mutablePayload = new HashMap<String, Object>();
            mutablePayload.put("key", "value");
            var event = ExecutionEvent.of(
                    EVENT_ID, EXECUTION_ID, null, null,
                    ExecutionEvent.AGENT_REGISTERED,
                    mutablePayload, Instant.now());

            mutablePayload.put("extra", "should not appear");

            assertThat(event.payload()).hasSize(1);
        }
    }
}
