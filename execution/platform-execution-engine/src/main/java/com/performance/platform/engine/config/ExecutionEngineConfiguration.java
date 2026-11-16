package com.performance.platform.engine.config;

import com.performance.platform.application.config.ExecutionConfig;
import com.performance.platform.application.ports.out.ExecutionRepository;
import com.performance.platform.engine.availability.AgentAvailabilityChecker;
import com.performance.platform.engine.correlation.TaskCorrelationTracker;
import com.performance.platform.engine.remote.TransportExecutionEventHandler;
import com.performance.platform.engine.remote.TransportStepDispatcher;
import com.performance.platform.engine.shared.StepDispatcher;
import com.performance.platform.transport.SignalChannel;
import com.performance.platform.transport.TaskChannel;

import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration de l'engine d'execution.
 *
 * <p>Depuis ADR-059, le moteur a un seul chemin d'execution : les strategies
 * sont les memes dans tous les modes, seule la racine de composition choisit
 * le transport et l'agent. Les beans ci-dessous ne dependent donc plus de
 * {@code runtime.mode}.</p>
 *
 * <p>Le dispatcher et le handler d'evenements ne sont crees que la ou une
 * execution peut avoir lieu : LOCAL et DISTRIBUTED + ORCHESTRATOR.</p>
 */
@Configuration
@EnableConfigurationProperties(ExecutionEngineProperties.class)
public class ExecutionEngineConfiguration {

    private static final String EXECUTOR_PRESENT =
            "'${runtime.mode:LOCAL}'.equals('LOCAL') || ('${runtime.mode:LOCAL}'.equals('DISTRIBUTED')"
            + " && '${runtime.role:NONE}'.equals('ORCHESTRATOR'))";

    /**
     * Bean {@link ExecutionConfig} dans tous les modes (ADR-059).
     */
    @Bean
    public ExecutionConfig executionConfig(ExecutionEngineProperties props) {
        return new ExecutionConfig(
                props.taskAvailabilityTimeout(),
                props.taskExecutionTimeout(),
                props.claimWindow()
        );
    }

    /**
     * Dispatcher unique : dispatch via les ports de transport (ADR-055, ADR-059).
     */
    @Bean
    @ConditionalOnExpression(EXECUTOR_PRESENT)
    public StepDispatcher stepDispatcher(
            TaskChannel taskChannel,
            SignalChannel signalChannel,
            AgentAvailabilityChecker availabilityChecker,
            TaskCorrelationTracker tracker,
            ExecutionConfig config,
            ApplicationEventPublisher eventPublisher) {
        return new TransportStepDispatcher(taskChannel, signalChannel, availabilityChecker,
                tracker, config, eventPublisher);
    }

    /**
     * Consomme les evenements d'execution et applique les grants.
     */
    @Bean
    @ConditionalOnExpression(EXECUTOR_PRESENT)
    public TransportExecutionEventHandler transportExecutionEventHandler(
            com.performance.platform.transport.EventChannel eventChannel,
            SignalChannel signalChannel,
            TaskCorrelationTracker tracker,
            ExecutionRepository executionRepository,
            ApplicationEventPublisher eventPublisher) {
        return new TransportExecutionEventHandler(eventChannel, signalChannel, tracker,
                executionRepository, eventPublisher);
    }

    /**
     * Dispatcher unique des signaux de cycle de vie (ADR-059).
     */
    @Bean
    @ConditionalOnExpression(EXECUTOR_PRESENT)
    public com.performance.platform.engine.ExecutionLifecycleDispatcher executionLifecycleDispatcher(
            SignalChannel signalChannel) {
        return new com.performance.platform.engine.lifecycle.TransportLifecycleDispatcher(signalChannel);
    }

    /**
     * ADR-058 : au demarrage, toute execution non terminale est marquee FAILED
     * (l'orchestrateur est unique et ne reprend pas une execution interrompue).
     */
    @Bean
    @ConditionalOnExpression(EXECUTOR_PRESENT)
    @ConditionalOnProperty(prefix = "platform.datasources.default", name = "url")
    public OrchestratorRestartFailureModel orchestratorRestartFailureModel(
            ExecutionRepository executionRepository,
            ApplicationEventPublisher eventPublisher) {
        return new OrchestratorRestartFailureModel(executionRepository, eventPublisher);
    }
}
