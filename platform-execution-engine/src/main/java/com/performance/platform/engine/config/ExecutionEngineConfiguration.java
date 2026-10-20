package com.performance.platform.engine.config;

import com.performance.platform.application.config.ExecutionConfig;
import com.performance.platform.application.ports.out.ExecutionRepository;
import com.performance.platform.engine.OutcomeEventPublisher;
import com.performance.platform.engine.availability.AgentAvailabilityChecker;
import com.performance.platform.engine.correlation.TaskCorrelationTracker;
import com.performance.platform.engine.local.LocalOutcomeEventPublisher;
import com.performance.platform.engine.local.LocalStepDispatcher;
import com.performance.platform.engine.local.TaskExecutorLookup;
import com.performance.platform.engine.remote.RemoteStepDispatcher;
import com.performance.platform.engine.remote.TransportExecutionEventHandler;
import com.performance.platform.engine.retry.RetryExecutor;
import com.performance.platform.engine.shared.StepDispatcher;
import com.performance.platform.transport.ExecutionTransport;

import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration de l'engine d'execution.
 *
 * <p>Le moteur est unique ({@code ExecutionEngine}) : seules les strategies
 * injectees different selon le mode (CF-03, PDR-050) :</p>
 * <ul>
 *   <li>LOCAL : {@link LocalStepDispatcher} + publication locale des events ;</li>
 *   <li>DISTRIBUTED + ORCHESTRATOR : {@link RemoteStepDispatcher} +
 *       {@link TransportExecutionEventHandler}.</li>
 * </ul>
 *
 * <p>Aucun {@code if/switch} sur {@code runtime.mode} : la selection est
 * declarative via les annotations conditionnelles Spring.</p>
 */
@Configuration
@EnableConfigurationProperties(ExecutionEngineProperties.class)
public class ExecutionEngineConfiguration {

    private static final String DISTRIBUTED_ORCHESTRATOR =
            "'${runtime.mode:LOCAL}'.equals('DISTRIBUTED')"
            + " && '${runtime.role:NONE}'.equals('ORCHESTRATOR')";

    /**
     * Bean {@link ExecutionConfig} conditionnel DISTRIBUTED.
     */
    @Bean
    @ConditionalOnProperty(name = "runtime.mode", havingValue = "DISTRIBUTED")
    public ExecutionConfig executionConfig(ExecutionEngineProperties props) {
        return new ExecutionConfig(
                props.taskAvailabilityTimeout(),
                props.taskExecutionTimeout(),
                props.workInProgressResetInterval(),
                props.completionPolicy(),
                props.resultSelectionPolicy()
        );
    }

    // ------------------------------------------------------------------
    // LOCAL
    // ------------------------------------------------------------------

    @Bean
    @ConditionalOnProperty(name = "runtime.mode", havingValue = "LOCAL")
    public StepDispatcher localStepDispatcher(TaskExecutorLookup taskExecutorLookup,
                                              RetryExecutor retryExecutor) {
        return new LocalStepDispatcher(taskExecutorLookup, retryExecutor);
    }

    @Bean
    @ConditionalOnProperty(name = "runtime.mode", havingValue = "LOCAL")
    public OutcomeEventPublisher localOutcomeEventPublisher(
            ApplicationEventPublisher eventPublisher) {
        return new LocalOutcomeEventPublisher(eventPublisher);
    }

    // ------------------------------------------------------------------
    // DISTRIBUTED + ORCHESTRATOR
    // ------------------------------------------------------------------

    @Bean
    @ConditionalOnExpression(DISTRIBUTED_ORCHESTRATOR)
    public StepDispatcher remoteStepDispatcher(
            ExecutionTransport transport,
            AgentAvailabilityChecker availabilityChecker,
            TaskCorrelationTracker tracker,
            ExecutionConfig config,
            ApplicationEventPublisher eventPublisher) {
        return new RemoteStepDispatcher(transport, availabilityChecker, tracker, config, eventPublisher);
    }

    /**
     * En DISTRIBUTED, les TaskCompleted/TaskFailed sont deja publies par le
     * {@link TransportExecutionEventHandler} avec l'identite reelle de l'agent.
     */
    @Bean
    @ConditionalOnExpression(DISTRIBUTED_ORCHESTRATOR)
    public OutcomeEventPublisher distributedOutcomeEventPublisher() {
        return (executionId, outcomes, phase) -> {
            // no-op : la publication est faite par le handler de transport
        };
    }

    @Bean
    @ConditionalOnExpression(DISTRIBUTED_ORCHESTRATOR)
    public TransportExecutionEventHandler transportExecutionEventHandler(
            ExecutionTransport transport,
            TaskCorrelationTracker tracker,
            ExecutionRepository executionRepository,
            ApplicationEventPublisher eventPublisher) {
        return new TransportExecutionEventHandler(
                transport, tracker, executionRepository, eventPublisher);
    }
}
