package com.performance.platform.observability.config;

import com.performance.platform.domain.id.AgentId;
import com.performance.platform.domain.id.ExecutionId;
import com.performance.platform.domain.id.ScenarioId;
import com.performance.platform.domain.id.TaskId;
import com.performance.platform.domain.scenario.Phase;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("ExecutionContextMdcFilter")
class ExecutionContextMdcFilterTest {

    private ExecutionContextMdcFilter filter;

    @BeforeEach
    void setUp() {
        filter = new ExecutionContextMdcFilter();
        MDC.clear();
    }

    @AfterEach
    void tearDown() {
        MDC.clear();
    }

    // ---- setExecutionId ----

    @Nested
    @DisplayName("setExecutionId")
    class SetExecutionIdTests {

        @Test
        @DisplayName("should put executionId value in MDC")
        void shouldSetExecutionId() {
            ExecutionId id = new ExecutionId("exec-001");
            filter.setExecutionId(id);
            assertThat(MDC.get(ExecutionContextMdcFilter.MDC_EXECUTION_ID))
                    .isEqualTo("exec-001");
        }

        @Test
        @DisplayName("should throw NPE on null executionId")
        void shouldThrowOnNullExecutionId() {
            assertThatThrownBy(() -> filter.setExecutionId(null))
                    .isInstanceOf(NullPointerException.class);
        }
    }

    // ---- setScenarioId ----

    @Nested
    @DisplayName("setScenarioId")
    class SetScenarioIdTests {

        @Test
        @DisplayName("should put scenarioId value in MDC")
        void shouldSetScenarioId() {
            ScenarioId id = new ScenarioId("scenario-001");
            filter.setScenarioId(id);
            assertThat(MDC.get(ExecutionContextMdcFilter.MDC_SCENARIO_ID))
                    .isEqualTo("scenario-001");
        }

        @Test
        @DisplayName("should throw NPE on null scenarioId")
        void shouldThrowOnNullScenarioId() {
            assertThatThrownBy(() -> filter.setScenarioId(null))
                    .isInstanceOf(NullPointerException.class);
        }
    }

    // ---- setTaskId ----

    @Nested
    @DisplayName("setTaskId")
    class SetTaskIdTests {

        @Test
        @DisplayName("should put taskId value in MDC")
        void shouldSetTaskId() {
            TaskId id = new TaskId("task-001");
            filter.setTaskId(id);
            assertThat(MDC.get(ExecutionContextMdcFilter.MDC_TASK_ID))
                    .isEqualTo("task-001");
        }

        @Test
        @DisplayName("should throw NPE on null taskId")
        void shouldThrowOnNullTaskId() {
            assertThatThrownBy(() -> filter.setTaskId(null))
                    .isInstanceOf(NullPointerException.class);
        }
    }

    // ---- setAgentId ----

    @Nested
    @DisplayName("setAgentId")
    class SetAgentIdTests {

        @Test
        @DisplayName("should put agentId value in MDC")
        void shouldSetAgentId() {
            AgentId id = new AgentId("agent-001");
            filter.setAgentId(id);
            assertThat(MDC.get(ExecutionContextMdcFilter.MDC_AGENT_ID))
                    .isEqualTo("agent-001");
        }

        @Test
        @DisplayName("should throw NPE on null agentId")
        void shouldThrowOnNullAgentId() {
            assertThatThrownBy(() -> filter.setAgentId(null))
                    .isInstanceOf(NullPointerException.class);
        }
    }

    // ---- setPhase ----

    @Nested
    @DisplayName("setPhase")
    class SetPhaseTests {

        @Test
        @DisplayName("should put PREPARATION phase in MDC")
        void shouldSetPreparationPhase() {
            filter.setPhase(Phase.PREPARATION);
            assertThat(MDC.get(ExecutionContextMdcFilter.MDC_PHASE))
                    .isEqualTo("PREPARATION");
        }

        @Test
        @DisplayName("should put INJECTION phase in MDC")
        void shouldSetInjectionPhase() {
            filter.setPhase(Phase.INJECTION);
            assertThat(MDC.get(ExecutionContextMdcFilter.MDC_PHASE))
                    .isEqualTo("INJECTION");
        }

        @Test
        @DisplayName("should put ASSERTION phase in MDC")
        void shouldSetAssertionPhase() {
            filter.setPhase(Phase.ASSERTION);
            assertThat(MDC.get(ExecutionContextMdcFilter.MDC_PHASE))
                    .isEqualTo("ASSERTION");
        }

        @Test
        @DisplayName("should throw NPE on null phase")
        void shouldThrowOnNullPhase() {
            assertThatThrownBy(() -> filter.setPhase(null))
                    .isInstanceOf(NullPointerException.class);
        }
    }

    // ---- clearAll ----

    @Nested
    @DisplayName("clearAll")
    class ClearAllTests {

        @Test
        @DisplayName("should remove all MDC keys after setting them")
        void shouldClearAllKeys() {
            filter.setExecutionId(new ExecutionId("exec-001"));
            filter.setScenarioId(new ScenarioId("scenario-001"));
            filter.setTaskId(new TaskId("task-001"));
            filter.setAgentId(new AgentId("agent-001"));
            filter.setPhase(Phase.INJECTION);

            filter.clearAll();

            assertThat(MDC.get(ExecutionContextMdcFilter.MDC_EXECUTION_ID)).isNull();
            assertThat(MDC.get(ExecutionContextMdcFilter.MDC_SCENARIO_ID)).isNull();
            assertThat(MDC.get(ExecutionContextMdcFilter.MDC_TASK_ID)).isNull();
            assertThat(MDC.get(ExecutionContextMdcFilter.MDC_AGENT_ID)).isNull();
            assertThat(MDC.get(ExecutionContextMdcFilter.MDC_PHASE)).isNull();
        }

        @Test
        @DisplayName("should not throw when MDC is already empty")
        void shouldNotThrowOnEmptyMdc() {
            filter.clearAll(); // no exception
            // verify MDC is still clean
            assertThat(MDC.get(ExecutionContextMdcFilter.MDC_EXECUTION_ID)).isNull();
        }
    }

    // ---- getContext ----

    @Nested
    @DisplayName("getContext")
    class GetContextTests {

        @Test
        @DisplayName("should return immutable snapshot of MDC context")
        void shouldReturnContextSnapshot() {
            filter.setExecutionId(new ExecutionId("exec-001"));
            filter.setTaskId(new TaskId("task-001"));

            Map<String, String> context = filter.getContext();
            assertThat(context).containsEntry(
                    ExecutionContextMdcFilter.MDC_EXECUTION_ID, "exec-001");
            assertThat(context).containsEntry(
                    ExecutionContextMdcFilter.MDC_TASK_ID, "task-001");

            assertThatThrownBy(() -> context.put("newKey", "value"))
                    .isInstanceOf(UnsupportedOperationException.class);
        }

        @Test
        @DisplayName("should return empty map when MDC is empty")
        void shouldReturnEmptyMap() {
            Map<String, String> context = filter.getContext();
            assertThat(context).isEmpty();
        }
    }

    // ---- try/finally pattern (integration of all operations) ----

    @Test
    @DisplayName("should support try/finally pattern without leaking MDC")
    void shouldSupportTryFinallyPattern() {
        filter.setExecutionId(new ExecutionId("exec-001"));
        filter.setTaskId(new TaskId("task-001"));

        try {
            assertThat(MDC.get(ExecutionContextMdcFilter.MDC_EXECUTION_ID))
                    .isEqualTo("exec-001");
            assertThat(MDC.get(ExecutionContextMdcFilter.MDC_TASK_ID))
                    .isEqualTo("task-001");
        } finally {
            filter.clearAll();
        }

        // Apres finally, MDC est propre
        assertThat(MDC.get(ExecutionContextMdcFilter.MDC_EXECUTION_ID)).isNull();
        assertThat(MDC.get(ExecutionContextMdcFilter.MDC_TASK_ID)).isNull();
    }

    // ---- @Component present ----

    @Test
    @DisplayName("should be annotated with @Component")
    void shouldBeAnnotatedWithComponent() {
        var annotation = ExecutionContextMdcFilter.class.getAnnotation(
                org.springframework.stereotype.Component.class);
        assertThat(annotation).isNotNull();
    }

    // ---- Constants ----

    @Test
    @DisplayName("should have correct MDC key constants")
    void shouldHaveCorrectMdcKeys() {
        assertThat(ExecutionContextMdcFilter.MDC_EXECUTION_ID).isEqualTo("executionId");
        assertThat(ExecutionContextMdcFilter.MDC_SCENARIO_ID).isEqualTo("scenarioId");
        assertThat(ExecutionContextMdcFilter.MDC_TASK_ID).isEqualTo("taskId");
        assertThat(ExecutionContextMdcFilter.MDC_AGENT_ID).isEqualTo("agentId");
        assertThat(ExecutionContextMdcFilter.MDC_PHASE).isEqualTo("phase");
    }
}
