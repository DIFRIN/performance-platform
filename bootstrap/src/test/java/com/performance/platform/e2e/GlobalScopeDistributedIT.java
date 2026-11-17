package com.performance.platform.e2e;

import com.performance.platform.agent.filter.DefaultTaskSpecializationFilter;
import com.performance.platform.agent.runtime.TaskAgentRuntime;
import com.performance.platform.application.config.ExecutionConfig;
import com.performance.platform.assertion.lifecycle.DefaultLifecycleSignalHandler;
import com.performance.platform.campaign.ExecutionEngine;
import com.performance.platform.campaign.plan.DefaultExecutionPlanBuilder;
import com.performance.platform.domain.agent.AgentCapabilities;
import com.performance.platform.domain.agent.AgentDescriptor;
import com.performance.platform.domain.agent.AgentState;
import com.performance.platform.domain.event.ScenarioFinished;
import com.performance.platform.domain.execution.ExecutionContext;
import com.performance.platform.domain.id.AgentId;
import com.performance.platform.domain.id.ExecutionId;
import com.performance.platform.domain.id.ScenarioId;
import com.performance.platform.domain.id.TaskId;
import com.performance.platform.domain.injection.ArrivalModel;
import com.performance.platform.domain.injection.Burst;
import com.performance.platform.domain.injection.LoadScope;
import com.performance.platform.domain.injection.WorkloadPlan;
import com.performance.platform.domain.report.Verdict;
import com.performance.platform.domain.scenario.AgentLossPolicy;
import com.performance.platform.domain.scenario.FanOut;
import com.performance.platform.domain.scenario.Phase;
import com.performance.platform.domain.scenario.ScenarioDefinition;
import com.performance.platform.domain.scenario.StepDefinition;
import com.performance.platform.domain.task.TaskResult;
import com.performance.platform.domain.task.TaskStatus;
import com.performance.platform.engine.availability.AgentAvailabilityChecker;
import com.performance.platform.engine.correlation.DefaultTaskCorrelationTracker;
import com.performance.platform.engine.lifecycle.TransportLifecycleDispatcher;
import com.performance.platform.engine.remote.TransportExecutionEventHandler;
import com.performance.platform.engine.remote.TransportStepDispatcher;
import com.performance.platform.injection.gatling.load.PlanScaler;
import com.performance.platform.plugin.TaskExecutor;
import com.performance.platform.transport.inmemory.InMemoryExecutionTransport;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * ISSUE-237 — scope global en distribue (ADR-067) : 2 agents sur le transport
 * en memoire se partagent un plan global ; {@code minAgents} bloque le run.
 * <p>
 * L'executeur d'injection applique le {@link PlanScaler} reel a la part recue
 * dans le contexte et comptabilise les requetes de sa part, sans lancer Gatling.
 */
@DisplayName("Global scope distributed (ISSUE-237)")
class GlobalScopeDistributedIT {

    private static final long TOTAL_REQUESTS = 10_000;
    private static final String INJECT = "inject";

    @Test
    @Timeout(60)
    @DisplayName("2 agents share the global plan: sum of requests ~ plan total (+-5%)")
    void shouldSplitGlobalPlanBetweenAgents() throws Exception {
        var counters = List.of(new AtomicLong(), new AtomicLong());
        var run = run(2, 1, counters);

        assertThat(run.verdict()).isEqualTo(Verdict.SUCCESS);
        long sum = counters.stream().mapToLong(AtomicLong::get).sum();
        assertThat((double) sum).isCloseTo(TOTAL_REQUESTS, within(TOTAL_REQUESTS * 0.05));
        assertThat(counters.get(0).get()).isPositive();
        assertThat(counters.get(1).get()).isPositive();
    }

    @Test
    @Timeout(60)
    @DisplayName("minAgents: 3 with 2 agents fails the step without any traffic")
    void shouldFailWithoutTrafficWhenMinAgentsNotMet() throws Exception {
        var counters = List.of(new AtomicLong(), new AtomicLong());
        var run = run(2, 3, counters);

        assertThat(run.verdict()).isNotEqualTo(Verdict.SUCCESS);
        assertThat(run.injectStatuses()).containsOnly(TaskStatus.FAILED);
        assertThat(counters.stream().mapToLong(AtomicLong::get).sum()).isZero();
    }

    // ------------------------------------------------------------------

    private record Run(Verdict verdict, List<TaskStatus> injectStatuses) {
    }

    private static Run run(int agentCount, int minAgents, List<AtomicLong> counters) throws Exception {
        var transport = new InMemoryExecutionTransport();
        var events = new CopyOnWriteArrayList<Object>();
        var repository = new LocalDistributedEquivalenceIT.InMemoryExecutionRepository();
        var tracker = new DefaultTaskCorrelationTracker();
        var dispatcher = new TransportStepDispatcher(
                transport, transport, alwaysAvailable(), tracker,
                new ExecutionConfig(Duration.ofSeconds(10), Duration.ofSeconds(30), Duration.ofMillis(500)),
                events::add);
        var lifecycle = new TransportLifecycleDispatcher(transport);
        new TransportExecutionEventHandler(transport, transport, tracker, repository, events::add);

        var agents = new java.util.ArrayList<TaskAgentRuntime>();
        try {
            for (int i = 0; i < agentCount; i++) {
                agents.add(agent(transport, "agent-" + i, counters.get(i)));
            }
            agents.forEach(TaskAgentRuntime::start);

            var engine = new ExecutionEngine(
                    new DefaultExecutionPlanBuilder(), repository, events::add, lifecycle, dispatcher);
            ExecutionId id = engine.execute(scenario(minAgents));

            Verdict verdict = events.stream()
                    .filter(ScenarioFinished.class::isInstance)
                    .map(ScenarioFinished.class::cast)
                    .map(ScenarioFinished::verdict)
                    .findFirst()
                    .orElseThrow(() -> new AssertionError("ScenarioFinished not published"));
            var state = repository.findById(id).orElseThrow();
            var statuses = new java.util.ArrayList<TaskStatus>();
            Object byAgent = state.context().store().get(INJECT);
            if (byAgent instanceof Map<?, ?> m) {
                m.values().forEach(r -> statuses.add(((TaskResult) r).status()));
            }
            if (statuses.isEmpty()) {
                // echec avant tout resultat d'agent : l'etape est FAILED au niveau de l'execution
                statuses.add(TaskStatus.FAILED);
            }
            return new Run(verdict, statuses);
        } finally {
            agents.forEach(TaskAgentRuntime::stop);
            transport.disconnect();
        }
    }

    private static TaskAgentRuntime agent(InMemoryExecutionTransport transport, String name, AtomicLong counter) {
        Set<String> supported = Set.of(INJECT);
        var descriptor = new AgentDescriptor(
                AgentId.of(name), name, "localhost", 0, null, supported,
                new AgentCapabilities(10, "1.0.0"), AgentState.OFFLINE,
                Instant.now(), Instant.now(), Duration.ofMinutes(5));
        return new TaskAgentRuntime(
                transport, transport, transport, transport,
                new DefaultTaskSpecializationFilter(supported, descriptor.id()),
                descriptor, Duration.ofSeconds(30),
                List.of(injectionExecutor(counter)), List.of(), null, null,
                new DefaultLifecycleSignalHandler(supported::contains));
    }

    private static TaskExecutor injectionExecutor(AtomicLong counter) {
        return new TaskExecutor() {
            @Override
            public TaskResult execute(ExecutionContext context, StepDefinition step) {
                var plan = new WorkloadPlan(ArrivalModel.OPEN, LoadScope.GLOBAL,
                        List.of(new Burst(TOTAL_REQUESTS, true)));
                var scaled = PlanScaler.scale(plan, context.share());
                long mine = scaled.stages().stream()
                        .filter(Burst.class::isInstance)
                        .mapToLong(s -> ((Burst) s).users())
                        .sum();
                counter.addAndGet(mine);
                return TaskResult.success(step.id(), INJECT, Duration.ofMillis(5), Map.of("requests", mine));
            }

            @Override
            public String getSupportedTaskName() {
                return INJECT;
            }
        };
    }

    private static ScenarioDefinition scenario(int minAgents) {
        var inject = new StepDefinition(
                TaskId.of(INJECT), INJECT, Phase.INJECTION, Map.of(), List.of(), null, null, null,
                FanOut.ALL, List.of(), Duration.ofSeconds(20), null, minAgents, AgentLossPolicy.ABORT);
        return new ScenarioDefinition(
                ScenarioId.of("global-scope"), "global-scope", "1.0.0",
                List.of(), Map.of(), List.of(inject), null, null);
    }

    private static AgentAvailabilityChecker alwaysAvailable() {
        return new AgentAvailabilityChecker() {
            @Override
            public void awaitAgentFor(String taskName, Duration timeout) {
            }

            @Override
            public boolean hasAgentFor(String taskName) {
                return true;
            }
        };
    }
}
