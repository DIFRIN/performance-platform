package com.performance.platform.e2e;

import com.performance.platform.application.ports.out.ExecutionRepository;
import com.performance.platform.domain.event.ScenarioFinished;
import com.performance.platform.domain.execution.ExecutionState;
import com.performance.platform.domain.execution.PhaseStatus;
import com.performance.platform.domain.id.AgentId;
import com.performance.platform.domain.id.ExecutionId;
import com.performance.platform.domain.id.ScenarioId;
import com.performance.platform.domain.id.TaskId;
import com.performance.platform.domain.report.Verdict;
import com.performance.platform.domain.scenario.Phase;
import com.performance.platform.domain.scenario.ScenarioDefinition;
import com.performance.platform.domain.scenario.StepDefinition;
import com.performance.platform.domain.task.TaskResult;
import com.performance.platform.domain.task.TaskStatus;
import com.performance.platform.campaign.ExecutionEngine;
import com.performance.platform.campaign.plan.DefaultExecutionPlanBuilder;
import com.performance.platform.plugin.TaskExecutor;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * T2 — le meme scenario produit le meme verdict et les memes statuts de
 * tasks en LOCAL et en DISTRIBUTED (transport in-memory + agent reel).
 */
@DisplayName("Local / Distributed equivalence (T2)")
class LocalDistributedEquivalenceIT {

    private static final List<String> TASK_NAMES = List.of("prep", "inject", "assert");

    @Test
    @Timeout(60)
    @DisplayName("same scenario yields the same verdict and task statuses")
    void shouldBeEquivalent() {
        ScenarioDefinition scenario = scenario();
        Map<String, TaskExecutor> executors = executors();
        Set<String> supportedTaskNames = Set.copyOf(TASK_NAMES);

        // ---- LOCAL (transport in-memory + agent embarque) ----
        var localEvents = new CopyOnWriteArrayList<Object>();
        var localRepository = new InMemoryExecutionRepository();
        var local = new LocalTransportHarness(localRepository, localEvents::add,
                List.copyOf(executors.values()), supportedTaskNames);
        local.start();
        try {
            var localEngine = new ExecutionEngine(
                    new DefaultExecutionPlanBuilder(),
                    localRepository,
                    localEvents::add,
                    local.lifecycleDispatcher(),
                    local.stepDispatcher());

            ExecutionId localId = localEngine.execute(scenario);

            // ---- DISTRIBUTED (transport in-memory dedie + agent enregistre) ----
            var remoteEvents = new CopyOnWriteArrayList<Object>();
            var remoteRepository = new InMemoryExecutionRepository();
            var remote = new LocalTransportHarness(remoteRepository, remoteEvents::add,
                    List.copyOf(executors.values()), supportedTaskNames);
            remote.start();
            try {
                var remoteEngine = new ExecutionEngine(
                        new DefaultExecutionPlanBuilder(),
                        remoteRepository,
                        remoteEvents::add,
                        remote.lifecycleDispatcher(),
                        remote.stepDispatcher());

                ExecutionId remoteId = remoteEngine.execute(scenario);

                ExecutionState localState = localRepository.findById(localId).orElseThrow();
                ExecutionState remoteState = remoteRepository.findById(remoteId).orElseThrow();

                assertThat(verdict(remoteEvents)).isEqualTo(verdict(localEvents));
                assertThat(verdict(remoteEvents)).isEqualTo(Verdict.SUCCESS);
                assertThat(remoteState.status()).isEqualTo(localState.status());
                assertThat(remoteState.phaseStatuses()).isEqualTo(localState.phaseStatuses());
                assertThat(taskStatuses(remoteState)).isEqualTo(taskStatuses(localState));
                assertThat(taskStatuses(remoteState).values())
                        .containsOnly(TaskStatus.SUCCESS)
                        .hasSize(TASK_NAMES.size());
            } finally {
                remote.close();
            }
        } finally {
            local.close();
        }
    }

    // ------------------------------------------------------------------
    // Fixtures
    // ------------------------------------------------------------------

    private static ScenarioDefinition scenario() {
        var prep = step("prep", "prep", Phase.PREPARATION);
        var inject = step("inject", "inject", Phase.INJECTION, "prep");
        var assertion = step("assert", "assert", Phase.ASSERTION, "inject");
        return new ScenarioDefinition(
                ScenarioId.of("equivalence"), "equivalence", "1.0.0",
                List.of(), Map.of(),
                List.of(prep, inject, assertion), null, null);
    }

    private static StepDefinition step(String id, String task, Phase phase, String... dependsOn) {
        List<TaskId> dependencies = new ArrayList<>();
        for (String dependency : dependsOn) {
            dependencies.add(TaskId.of(dependency));
        }
        return new StepDefinition(
                TaskId.of(id), task, phase, Map.of(), dependencies, List.of(),
                Duration.ofSeconds(10), null);
    }

    private static Map<String, TaskExecutor> executors() {
        Map<String, TaskExecutor> byName = new LinkedHashMap<>();
        for (String taskName : TASK_NAMES) {
            byName.put(taskName, new TaskExecutor() {
                @Override
                public TaskResult execute(com.performance.platform.domain.execution.ExecutionContext context,
                                          StepDefinition step) {
                    return TaskResult.success(step.id(), taskName, Duration.ofMillis(5),
                            Map.of("task", taskName));
                }

                @Override
                public String getSupportedTaskName() {
                    return taskName;
                }
            });
        }
        return byName;
    }

    private static Verdict verdict(List<Object> events) {
        return events.stream()
                .filter(ScenarioFinished.class::isInstance)
                .map(ScenarioFinished.class::cast)
                .map(ScenarioFinished::verdict)
                .findFirst()
                .orElseThrow(() -> new AssertionError("ScenarioFinished not published"));
    }

    /**
     * Statut par taskId, independamment de l'agent qui l'a execute.
     */
    private static Map<TaskId, TaskStatus> taskStatuses(ExecutionState state) {
        Map<TaskId, TaskStatus> statuses = new LinkedHashMap<>();
        state.context().store().forEach((taskId, byAgent) -> {
            Optional<TaskResult> any = byAgent.values().stream().findFirst();
            any.ifPresent(result -> statuses.put(TaskId.of(taskId), result.status()));
        });
        return statuses;
    }

    /** Repository en memoire minimal, partage par les deux moteurs. */
    static final class InMemoryExecutionRepository implements ExecutionRepository {

        private final Map<ExecutionId, ExecutionState> states = new ConcurrentHashMap<>();
        private final Map<ExecutionId, Map<TaskId, Map<AgentId, TaskResult>>> results =
                new ConcurrentHashMap<>();

        @Override
        public void save(ExecutionState state) {
            states.put(state.id(), state);
        }

        @Override
        public Optional<ExecutionState> findById(ExecutionId id) {
            return Optional.ofNullable(states.get(id));
        }

        @Override
        public void updatePhase(ExecutionId id, Phase phase, PhaseStatus status) {
            ExecutionState current = states.get(id);
            if (current != null) {
                Map<Phase, PhaseStatus> updated = new HashMap<>(current.phaseStatuses());
                updated.put(phase, status);
                states.put(id, new ExecutionState(current.id(), current.scenarioId(),
                        current.status(), updated, current.context(),
                        current.startedAt(), Instant.now()));
            }
        }

        @Override
        public void saveTaskResult(ExecutionId id, TaskId taskId, AgentId agentId, TaskResult result) {
            results.computeIfAbsent(id, ignored -> new ConcurrentHashMap<>())
                    .computeIfAbsent(taskId, ignored -> new ConcurrentHashMap<>())
                    .put(agentId, result);
        }

        @Override
        public Map<AgentId, TaskResult> getTaskResults(ExecutionId id, TaskId taskId) {
            return Map.copyOf(results.getOrDefault(id, Map.of())
                    .getOrDefault(taskId, Map.of()));
        }

        @Override
        public List<ExecutionState> findAll(int limit) {
            return states.values().stream().limit(limit).toList();
        }

        @Override
        public Map<TaskId, Map<AgentId, TaskResult>> findAllTaskResults(ExecutionId id) {
            return Map.copyOf(results.getOrDefault(id, Map.of()));
        }

        @Override
        public void deleteById(ExecutionId id) {
            states.remove(id);
            results.remove(id);
        }
    }
}
