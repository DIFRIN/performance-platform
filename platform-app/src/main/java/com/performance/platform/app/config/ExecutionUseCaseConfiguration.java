package com.performance.platform.app.config;

import com.performance.platform.application.ports.in.DeleteExecutionUseCase;
import com.performance.platform.application.ports.in.ListExecutionsUseCase;
import com.performance.platform.application.ports.out.ExecutionRepository;
import com.performance.platform.application.usecase.DeleteExecutionService;
import com.performance.platform.application.usecase.ExecutionProgressCalculator;
import com.performance.platform.application.usecase.ListExecutionsService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration Spring pour les beans du use case execution (ISSUE-121).
 * Declare les implementations de {@link ListExecutionsUseCase},
 * {@link DeleteExecutionUseCase} et {@link ExecutionProgressCalculator}.
 * <p>
 * Ces classes n'ont pas d'annotation Spring directement (platform-application
 * est framework-agnostique), d'ou la configuration explicite ici.
 */
@Configuration
public class ExecutionUseCaseConfiguration {

    /**
     * Expose {@link ListExecutionsService} comme implementation de {@link ListExecutionsUseCase}.
     *
     * @param repository le repository d'execution
     * @return l'implémentation du use case
     */
    @Bean
    public ListExecutionsUseCase listExecutionsUseCase(ExecutionRepository repository) {
        return new ListExecutionsService(repository);
    }

    /**
     * Expose {@link DeleteExecutionService} comme implementation de {@link DeleteExecutionUseCase}.
     *
     * @param repository le repository d'execution
     * @return l'implementation du use case
     */
    @Bean
    public DeleteExecutionUseCase deleteExecutionUseCase(ExecutionRepository repository) {
        return new DeleteExecutionService(repository);
    }

    /**
     * Expose {@link ExecutionProgressCalculator} comme bean Spring.
     * Sans etat — peut etre partage entre plusieurs controllers.
     *
     * @return le calculateur de progression
     */
    @Bean
    public ExecutionProgressCalculator executionProgressCalculator() {
        return new ExecutionProgressCalculator();
    }
}
