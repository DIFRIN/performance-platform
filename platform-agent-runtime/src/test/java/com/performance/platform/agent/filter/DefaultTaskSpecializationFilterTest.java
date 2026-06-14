package com.performance.platform.agent.filter;

import com.performance.platform.domain.execution.PartialExecutionContext;
import com.performance.platform.domain.execution.RetryPolicy;
import com.performance.platform.domain.id.AgentId;
import com.performance.platform.domain.id.ExecutionId;
import com.performance.platform.domain.id.MessageId;
import com.performance.platform.domain.id.ScenarioId;
import com.performance.platform.domain.id.TaskId;
import com.performance.platform.domain.scenario.Phase;
import com.performance.platform.domain.scenario.StepDefinition;
import com.performance.platform.transport.message.TaskExecutionRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("TaskSpecializationFilter")
class DefaultTaskSpecializationFilterTest {

    private static final AgentId AGENT_ID = AgentId.generate();
    private static final ExecutionId EXECUTION_ID = ExecutionId.generate();
    private static final ScenarioId SCENARIO_ID = ScenarioId.of("scenario-1");
    private static final MessageId MESSAGE_ID = MessageId.generate();

    private DefaultTaskSpecializationFilter filter;
    private Set<String> supportedTaskNames;

    @BeforeEach
    void setUp() {
        supportedTaskNames = new HashSet<>(Set.of("http-get", "kafka-produce", "database-query"));
        filter = new DefaultTaskSpecializationFilter(supportedTaskNames, AGENT_ID);
    }

    // === Fixtures ===

    private TaskExecutionRequest requestForTask(String taskName) {
        var step = new StepDefinition(
                TaskId.of(taskName),
                taskName,
                Phase.INJECTION,
                Map.of(),
                List.of(),
                List.of(),
                Duration.ofSeconds(30),
                new RetryPolicy(3, Duration.ofMillis(100), 2.0, Duration.ofSeconds(5), Set.of(RuntimeException.class))
        );
        return new TaskExecutionRequest(
                MESSAGE_ID,
                EXECUTION_ID,
                step,
                new PartialExecutionContext(EXECUTION_ID, SCENARIO_ID, Map.of()),
                Instant.now(),
                new RetryPolicy(3, Duration.ofMillis(100), 2.0, Duration.ofSeconds(5), Set.of(RuntimeException.class))
        );
    }

    // === Tests ===

    @Nested
    @DisplayName("Responsible — tâche supportée")
    class ResponsibleCases {

        @Test
        @DisplayName("should return Responsible for supported task name")
        void shouldReturnResponsible() {
            var result = filter.filter(requestForTask("http-get"));

            assertThat(result).isInstanceOf(TaskFilterResult.Responsible.class);
            var responsible = (TaskFilterResult.Responsible) result;
            assertThat(responsible.messageId()).isEqualTo(MESSAGE_ID);
            assertThat(responsible.agentId()).isEqualTo(AGENT_ID);
        }

        @Test
        @DisplayName("should return Responsible for each supported task")
        void shouldReturnResponsibleForEachSupportedTask() {
            for (var taskName : supportedTaskNames) {
                var result = filter.filter(requestForTask(taskName));
                assertThat(result).isInstanceOf(TaskFilterResult.Responsible.class);
            }
        }

        @Test
        @DisplayName("should match via taskName string equality")
        void shouldMatchViaTaskNameEquality() {
            // kafka-produce est supporté
            var result = filter.filter(requestForTask("kafka-produce"));
            assertThat(result).isInstanceOf(TaskFilterResult.Responsible.class);
        }
    }

    @Nested
    @DisplayName("NotResponsible — tâche non supportée")
    class NotResponsibleCases {

        @Test
        @DisplayName("should return NotResponsible for unsupported task name")
        void shouldReturnNotResponsible() {
            var result = filter.filter(requestForTask("grpc-call"));

            assertThat(result).isInstanceOf(TaskFilterResult.NotResponsible.class);
            var notResponsible = (TaskFilterResult.NotResponsible) result;
            assertThat(notResponsible.messageId()).isEqualTo(MESSAGE_ID);
            assertThat(notResponsible.agentId()).isEqualTo(AGENT_ID);
        }

        @Test
        @DisplayName("should return NotResponsible for empty supportedTaskNames")
        void shouldReturnNotResponsibleForEmptySet() {
            var emptyFilter = new DefaultTaskSpecializationFilter(Set.of(), AGENT_ID);
            var result = emptyFilter.filter(requestForTask("http-get"));
            assertThat(result).isInstanceOf(TaskFilterResult.NotResponsible.class);
        }

        @Test
        @DisplayName("should return NotResponsible for unknown task name")
        void shouldNotMatchSimilarButDifferentName() {
            var result = filter.filter(requestForTask("http-get-extended"));
            assertThat(result).isInstanceOf(TaskFilterResult.NotResponsible.class);
        }
    }

    @Nested
    @DisplayName("Pattern matching — sealed interface exhaustiveness")
    class PatternMatching {

        @Test
        @DisplayName("should enable exhaustive switch on TaskFilterResult")
        void shouldEnableExhaustiveSwitch() {
            var result = filter.filter(requestForTask("http-get"));

            var description = switch (result) {
                case TaskFilterResult.Responsible r -> "responsible: " + r.messageId().value();
                case TaskFilterResult.NotResponsible nr -> "not-responsible: " + nr.messageId().value();
            };

            assertThat(description).startsWith("responsible:");
        }

        @Test
        @DisplayName("should enable pattern matching for NotResponsible")
        void shouldEnablePatternMatchingForNotResponsible() {
            var result = filter.filter(requestForTask("unknown-task"));

            if (result instanceof TaskFilterResult.NotResponsible nr) {
                assertThat(nr.messageId()).isEqualTo(MESSAGE_ID);
                assertThat(nr.agentId()).isEqualTo(AGENT_ID);
            } else {
                throw new AssertionError("Expected NotResponsible");
            }
        }
    }

    @Nested
    @DisplayName("Null rejection")
    class NullRejection {

        @Test
        @DisplayName("should reject null supportedTaskNames")
        void shouldRejectNullSupportedTaskNames() {
            assertThatThrownBy(() -> new DefaultTaskSpecializationFilter(null, AGENT_ID))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("supportedTaskNames");
        }

        @Test
        @DisplayName("should reject null agentId")
        void shouldRejectNullAgentId() {
            assertThatThrownBy(() -> new DefaultTaskSpecializationFilter(Set.of("http"), null))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("agentId");
        }

        @Test
        @DisplayName("should reject null request in filter")
        void shouldRejectNullRequestInFilter() {
            assertThatThrownBy(() -> filter.filter(null))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("request");
        }
    }

    @Nested
    @DisplayName("Immutability")
    class Immutability {

        @Test
        @DisplayName("should defensively copy supportedTaskNames")
        void shouldDefensivelyCopyTaskNames() {
            var mutable = new HashSet<>(Set.of("task-a"));
            var f = new DefaultTaskSpecializationFilter(mutable, AGENT_ID);

            mutable.add("task-b"); // mutation externe

            // task-b ne devrait pas être reconnu car la copie a été faite à la construction
            var result = f.filter(requestForTask("task-b"));
            assertThat(result).isInstanceOf(TaskFilterResult.NotResponsible.class);
        }
    }

    @Nested
    @DisplayName("TaskFilterResult records — null rejection")
    class TaskFilterResultNullRejection {

        @Test
        @DisplayName("Responsible should reject null messageId")
        void responsibleShouldRejectNullMessageId() {
            assertThatThrownBy(() -> new TaskFilterResult.Responsible(null, AGENT_ID))
                    .isInstanceOf(NullPointerException.class);
        }

        @Test
        @DisplayName("Responsible should reject null agentId")
        void responsibleShouldRejectNullAgentId() {
            assertThatThrownBy(() -> new TaskFilterResult.Responsible(MESSAGE_ID, null))
                    .isInstanceOf(NullPointerException.class);
        }

        @Test
        @DisplayName("NotResponsible should reject null messageId")
        void notResponsibleShouldRejectNullMessageId() {
            assertThatThrownBy(() -> new TaskFilterResult.NotResponsible(null, AGENT_ID))
                    .isInstanceOf(NullPointerException.class);
        }

        @Test
        @DisplayName("NotResponsible should reject null agentId")
        void notResponsibleShouldRejectNullAgentId() {
            assertThatThrownBy(() -> new TaskFilterResult.NotResponsible(MESSAGE_ID, null))
                    .isInstanceOf(NullPointerException.class);
        }

        @Test
        @DisplayName("Responsible should store correct values")
        void responsibleShouldStoreValues() {
            var r = new TaskFilterResult.Responsible(MESSAGE_ID, AGENT_ID);
            assertThat(r.messageId()).isEqualTo(MESSAGE_ID);
            assertThat(r.agentId()).isEqualTo(AGENT_ID);
        }

        @Test
        @DisplayName("NotResponsible should store correct values")
        void notResponsibleShouldStoreValues() {
            var nr = new TaskFilterResult.NotResponsible(MESSAGE_ID, AGENT_ID);
            assertThat(nr.messageId()).isEqualTo(MESSAGE_ID);
            assertThat(nr.agentId()).isEqualTo(AGENT_ID);
        }
    }
}
