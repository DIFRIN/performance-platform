package com.performance.platform.engine.support;

import com.performance.platform.domain.execution.ExecutionContext;
import com.performance.platform.domain.execution.ExecutionStep;
import com.performance.platform.domain.id.AgentId;
import com.performance.platform.domain.task.TaskResult;
import com.performance.platform.engine.shared.StepDispatchResult;
import com.performance.platform.engine.shared.StepDispatcher;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Test double de {@link StepDispatcher}.
 * <p>
 * Le dispatcher n'a plus une seule methode (ADR-054, ADR-059) : les tests
 * fournissent ici les handlers de dispatch, de START et de STOP d'un service,
 * et peuvent inspecter les etapes recues.
 */
public final class StubStepDispatcher implements StepDispatcher {

    /** Agent unique utilise par le transport en memoire des tests. */
    public static final AgentId AGENT = AgentId.of("agent-local");

    @FunctionalInterface
    public interface StepHandler {
        List<StepDispatchResult> handle(ExecutionStep step, ExecutionContext context);
    }

    @FunctionalInterface
    public interface ServiceHandler {
        void handle(ExecutionStep step, ExecutionContext context);
    }

    private StepHandler dispatchHandler = (step, context) -> success(step, Map.of());
    private ServiceHandler startHandler = (step, context) -> { };
    private StepHandler stopHandler = (step, context) -> success(step, Map.of());

    private final List<ExecutionStep> dispatchedSteps = new CopyOnWriteArrayList<>();
    private final List<ExecutionStep> startedServices = new CopyOnWriteArrayList<>();
    private final List<ExecutionStep> stoppedServices = new CopyOnWriteArrayList<>();

    public StubStepDispatcher() {
    }

    public StubStepDispatcher(StepHandler dispatchHandler) {
        this.dispatchHandler = dispatchHandler;
    }

    public StubStepDispatcher onDispatch(StepHandler handler) {
        this.dispatchHandler = handler;
        return this;
    }

    public StubStepDispatcher onStartService(ServiceHandler handler) {
        this.startHandler = handler;
        return this;
    }

    public StubStepDispatcher onStopService(StepHandler handler) {
        this.stopHandler = handler;
        return this;
    }

    @Override
    public List<StepDispatchResult> dispatch(ExecutionStep execStep, ExecutionContext context) {
        dispatchedSteps.add(execStep);
        return dispatchHandler.handle(execStep, context);
    }

    @Override
    public void startService(ExecutionStep execStep, ExecutionContext context) {
        startedServices.add(execStep);
        startHandler.handle(execStep, context);
    }

    @Override
    public List<StepDispatchResult> stopService(ExecutionStep execStep, ExecutionContext context) {
        stoppedServices.add(execStep);
        return stopHandler.handle(execStep, context);
    }

    public List<ExecutionStep> dispatchedSteps() {
        return List.copyOf(dispatchedSteps);
    }

    public List<String> dispatchedTaskIds() {
        return dispatchedSteps.stream().map(step -> step.step().id().value()).toList();
    }

    public List<ExecutionStep> startedServices() {
        return List.copyOf(startedServices);
    }

    public List<ExecutionStep> stoppedServices() {
        return List.copyOf(stoppedServices);
    }

    /** Resultat SUCCESS pour l'etape, produit par {@link #AGENT}. */
    public static List<StepDispatchResult> success(ExecutionStep step, Map<String, Object> outputs) {
        return List.of(new StepDispatchResult(AGENT, TaskResult.success(
                step.step().id(), step.step().taskName(), Duration.ofMillis(1), outputs)));
    }

    /** Resultat quelconque pour l'etape, produit par {@link #AGENT}. */
    public static List<StepDispatchResult> result(ExecutionStep step, TaskResult result) {
        return List.of(new StepDispatchResult(AGENT, result));
    }

    /** Resultat FAILED pour l'etape, produit par {@link #AGENT}. */
    public static List<StepDispatchResult> failure(ExecutionStep step, String message) {
        return List.of(new StepDispatchResult(AGENT, TaskResult.failed(
                step.step().id(), step.step().taskName(), Duration.ofMillis(1), message, null)));
    }
}
