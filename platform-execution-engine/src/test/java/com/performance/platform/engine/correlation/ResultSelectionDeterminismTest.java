package com.performance.platform.engine.correlation;

import com.performance.platform.application.config.ResultSelectionPolicy;
import com.performance.platform.domain.id.AgentId;
import com.performance.platform.domain.id.ExecutionId;
import com.performance.platform.domain.id.MessageId;
import com.performance.platform.domain.id.TaskId;
import com.performance.platform.domain.task.TaskResult;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * T6 — la selection multi-claim est deterministe sur 1 000 executions.
 * <p>
 * L'ordre de completion est enregistre explicitement par le tracker :
 * la selection ne depend jamais de l'ordre d'iteration d'une map.
 */
@DisplayName("Result selection determinism (T6)")
class ResultSelectionDeterminismTest {

    private static final TaskId TASK_ID = TaskId.of("load-step");

    @Test
    @DisplayName("FIRST_COMPLETED selects the first completing agent 1000/1000 times")
    void firstCompletedIsDeterministic() {
        for (int i = 0; i < 1_000; i++) {
            var tracker = new DefaultTaskCorrelationTracker();
            MessageId messageId = dispatchAndComplete(tracker, "agent-b", "agent-a");

            assertThat(tracker.selectResult(messageId, ResultSelectionPolicy.FIRST_COMPLETED))
                    .hasValueSatisfying(result -> assertThat(result.outputs())
                            .containsEntry("winner", "agent-b"));
        }
    }

    @Test
    @DisplayName("LAST_COMPLETED selects the last completing agent 1000/1000 times")
    void lastCompletedIsDeterministic() {
        for (int i = 0; i < 1_000; i++) {
            var tracker = new DefaultTaskCorrelationTracker();
            MessageId messageId = dispatchAndComplete(tracker, "agent-b", "agent-a");

            assertThat(tracker.selectResult(messageId, ResultSelectionPolicy.LAST_COMPLETED))
                    .hasValueSatisfying(result -> assertThat(result.outputs())
                            .containsEntry("winner", "agent-a"));
        }
    }

    @Test
    @DisplayName("resultsInCompletionOrder preserves the observation order")
    void resultsInCompletionOrderIsStable() {
        var tracker = new DefaultTaskCorrelationTracker();
        MessageId messageId = dispatchAndComplete(tracker, "agent-b", "agent-a");

        List<TaskResult> ordered = tracker.resultsInCompletionOrder(messageId);

        assertThat(ordered).hasSize(2);
        assertThat(ordered.get(0).outputs()).containsEntry("winner", "agent-b");
        assertThat(ordered.get(1).outputs()).containsEntry("winner", "agent-a");
    }

    @Test
    @DisplayName("selection is empty when no agent completed")
    void selectionIsEmptyWithoutCompletion() {
        var tracker = new DefaultTaskCorrelationTracker();
        MessageId messageId = MessageId.generate();
        tracker.trackDispatched(messageId, TASK_ID, ExecutionId.generate());

        assertThat(tracker.selectResult(messageId, ResultSelectionPolicy.FIRST_COMPLETED)).isEmpty();
        assertThat(tracker.selectResult(messageId, ResultSelectionPolicy.LAST_COMPLETED)).isEmpty();
        assertThat(tracker.resultsInCompletionOrder(messageId)).isEmpty();
    }

    private static MessageId dispatchAndComplete(DefaultTaskCorrelationTracker tracker,
                                                 String firstAgent, String secondAgent) {
        MessageId messageId = MessageId.generate();
        tracker.trackDispatched(messageId, TASK_ID, ExecutionId.generate());
        tracker.onClaimed(messageId, AgentId.of(firstAgent));
        tracker.onClaimed(messageId, AgentId.of(secondAgent));
        tracker.onCompleted(messageId, AgentId.of(firstAgent), result(firstAgent));
        tracker.onCompleted(messageId, AgentId.of(secondAgent), result(secondAgent));
        return messageId;
    }

    private static TaskResult result(String agent) {
        return TaskResult.success(TASK_ID, "load-step", Duration.ofMillis(10),
                Map.of("winner", agent));
    }
}
