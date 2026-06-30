package com.performance.platform.engine.config;

import com.performance.platform.application.config.ExecutionConfig;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration de l'engine d'execution.
 *
 * <p>Expose le bean {@link ExecutionConfig} uniquement en mode DISTRIBUTED,
 * car seul le {@code RemoteExecutionEngine} en a besoin. Le record
 * {@link ExecutionConfig} reste framework-free dans {@code platform-application} ;
 * seul le binding/bean vit dans l'engine (ADR-026 D1).</p>
 *
 * <p>Active {@link ExecutionEngineProperties} pour le binding
 * {@code @ConfigurationProperties} du prefixe {@code execution.*}.</p>
 */
@Configuration
@EnableConfigurationProperties(ExecutionEngineProperties.class)
public class ExecutionEngineConfiguration {

    /**
     * Bean {@link ExecutionConfig} conditionnel DISTRIBUTED.
     * <p>
     * Les valeurs sont lues depuis les proprietes {@code execution.*}
     * avec les defauts definis dans {@link ExecutionEngineProperties}.
     */
    @Bean
    @ConditionalOnProperty(name = "runtime.mode", havingValue = "DISTRIBUTED")
    public ExecutionConfig executionConfig(ExecutionEngineProperties props) {
        return new ExecutionConfig(
                props.taskAvailabilityTimeout(),
                props.taskExecutionTimeout(),
                props.workInProgressResetInterval(),
                props.completionPolicy()
        );
    }
}
