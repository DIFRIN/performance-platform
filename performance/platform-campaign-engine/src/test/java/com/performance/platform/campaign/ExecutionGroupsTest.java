package com.performance.platform.campaign;

import com.performance.platform.application.ports.out.ExecutionRepository;
import com.performance.platform.domain.execution.ExecutionContext;
import com.performance.platform.domain.execution.ExecutionPlan;
import com.performance.platform.domain.execution.ExecutionStep;
import com.performance.platform.domain.id.ExecutionId;
import com.performance.platform.domain.id.ScenarioId;
import com.performance.platform.domain.id.TaskId;
import com.performance.platform.domain.scenario.ExecutionMode;
import com.performance.platform.domain.scenario.Phase;
import com.performance.platform.domain.scenario.ScenarioDefinition;
import com.performance.platform.domain.scenario.StepDefinition;
import com.performance.platform.domain.task.TaskResult;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class ExecutionGroupsTest {

    @Test
    void shouldSerializeTheWholeScenarioBeforePreparationAndReleaseAfterFailure() throws Exception {
        var firstEntered = new CountDownLatch(1);
        var secondEntered = new CountDownLatch(1);
        var allowFirst = new CountDownLatch(1);
        var secondFinished = new CountDownLatch(1);
        var first = new AtomicBoolean(true);
        var step = new StepDefinition(TaskId.of("prep"), "mock-server", Phase.PREPARATION,
                Map.of(), List.of(), List.of(), Duration.ofSeconds(10), null);
        var engine = new ExecutionEngine(scenario -> {
            var id = ExecutionId.generate();
            return new ExecutionPlan(id, scenario.id(), List.of(new ExecutionStep(step, List.of(), 0, java.util.Set.of())),
                    List.of(), List.of(), List.of(), ExecutionContext.initial(id, scenario.id()));
        }, mock(ExecutionRepository.class), event -> {
            if (event instanceof com.performance.platform.domain.event.ScenarioFinished finished
                    && finished.scenarioId().value().equals("second")) {
                secondFinished.countDown();
            }
        }, signal -> {}, (executionStep, context) -> {
            if (first.getAndSet(false)) {
                firstEntered.countDown();
                try {
                    if (!allowFirst.await(5, TimeUnit.SECONDS)) {
                        throw new IllegalStateException("Test did not release first execution");
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException(e);
                }
                return TaskResult.failed(step.id(), step.taskName(), Duration.ZERO, "expected failure", null);
            }
            secondEntered.countDown();
            return TaskResult.success(step.id(), step.taskName(), Duration.ZERO, Map.of());
        }, (id, outcomes, phase) -> {});

        engine.submit(scenario("first", step));
        assertThat(firstEntered.await(5, TimeUnit.SECONDS)).isTrue();
        engine.submit(scenario("second", step));
        try {
            assertThat(secondEntered.await(200, TimeUnit.MILLISECONDS)).isFalse();
        } finally {
            allowFirst.countDown();
        }
        assertThat(secondEntered.await(5, TimeUnit.SECONDS)).isTrue();
        assertThat(secondFinished.await(5, TimeUnit.SECONDS)).isTrue();
    }

    @Test
    void shouldAllowDifferentGroupsAndCancelWaitingWithoutLeakingTheGroup() throws Exception {
        var groups = new ExecutionGroups();
        assertThat(groups.acquire("port-8090", new AtomicBoolean())).isTrue();
        assertThat(groups.acquire("port-8091", new AtomicBoolean())).isTrue();
        var cancelled = new AtomicBoolean();
        try (var threads = java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor()) {
            var waiting = threads.submit(() -> groups.acquire("port-8090", cancelled));
            cancelled.set(true);
            assertThat(waiting.get(2, TimeUnit.SECONDS)).isFalse();
        }
        groups.release("port-8090");
        groups.release("port-8091");
        assertThat(groups.acquire("port-8090", new AtomicBoolean())).isTrue();
        groups.release("port-8090");
    }

    private static ScenarioDefinition scenario(String id, StepDefinition step) {
        return new ScenarioDefinition(ScenarioId.of(id), id, "1", List.of(),
                Map.of("executionGroup", "port-8090"), ExecutionMode.LOCAL, List.of(step), Map.of());
    }
}
