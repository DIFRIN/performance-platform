package com.performance.platform.runtime;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

import java.util.HashMap;
import java.util.Map;

/**
 * Applique la priorite env var > property au bootstrap Spring (ADR-006).
 * <p>
 * Lit les variables d'environnement {@code RUNTIME_MODE}, {@code MODE},
 * et {@code TRANSPORT_TYPE} et les reecrit en properties Spring
 * ({@code runtime.mode}, {@code runtime.role}, {@code transport.type})
 * AVANT la creation des beans, pour que les annotations
 * {@code @ConditionalOnProperty} fonctionnent correctement.
 * <p>
 * Enregistre via {@code META-INF/spring.factories} :
 * <pre>
 * org.springframework.boot.env.EnvironmentPostProcessor=\
 *   com.performance.platform.runtime.RuntimeConfigEnvironmentPostProcessor
 * </pre>
 */
public class RuntimeConfigEnvironmentPostProcessor implements EnvironmentPostProcessor {

    static final String PROPERTY_SOURCE_NAME = "runtimeConfigOverrides";

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment,
                                        SpringApplication application) {
        var overrides = new HashMap<String, Object>();

        // RUNTIME_MODE env var → runtime.mode property
        var runtimeMode = System.getenv(RuntimeConfigResolver.ENV_RUNTIME_MODE);
        if (runtimeMode != null && !runtimeMode.isBlank()) {
            overrides.put(RuntimeConfigResolver.PROP_RUNTIME_MODE, runtimeMode.strip());
        }

        // MODE env var → runtime.role property
        var mode = System.getenv(RuntimeConfigResolver.ENV_MODE);
        if (mode != null && !mode.isBlank()) {
            overrides.put(RuntimeConfigResolver.PROP_RUNTIME_ROLE, mode.strip());
        }

        // TRANSPORT_TYPE env var → transport.type property
        var transportType = System.getenv(RuntimeConfigResolver.ENV_TRANSPORT_TYPE);
        if (transportType != null && !transportType.isBlank()) {
            overrides.put(RuntimeConfigResolver.PROP_TRANSPORT_TYPE, transportType.strip());
        }

        if (!overrides.isEmpty()) {
            environment.getPropertySources()
                    .addFirst(new MapPropertySource(PROPERTY_SOURCE_NAME, overrides));
        }
    }
}
