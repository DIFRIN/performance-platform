package com.performance.platform.application.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Tests de ExecutionConfig — ADR-055/ADR-058 : plus de politiques globales,
 * ajout de la fenetre de claim.
 */
@DisplayName("ExecutionConfig")
class ExecutionConfigTest {

    @Test
    @DisplayName("valeurs fournies conservees")
    void keepsProvidedValues() {
        var config = new ExecutionConfig(
                Duration.ofSeconds(30), Duration.ofMinutes(10), Duration.ofMillis(250));

        assertEquals(Duration.ofSeconds(30), config.taskAvailabilityTimeout());
        assertEquals(Duration.ofMinutes(10), config.taskExecutionTimeout());
        assertEquals(Duration.ofMillis(250), config.claimWindow());
    }

    @Test
    @DisplayName("valeurs null remplacees par les defauts")
    void defaultsNullValues() {
        var config = new ExecutionConfig(null, null, null);

        assertEquals(Duration.ofSeconds(120), config.taskAvailabilityTimeout());
        assertEquals(Duration.ofSeconds(300), config.taskExecutionTimeout());
        assertEquals(Duration.ofSeconds(1), config.claimWindow());
    }

    @Test
    @DisplayName("ExecutionConfig est un record immuable")
    void isImmutableRecord() {
        var config = new ExecutionConfig(Duration.ofSeconds(1), Duration.ofSeconds(2), Duration.ofSeconds(1));
        assertEquals(config, new ExecutionConfig(Duration.ofSeconds(1), Duration.ofSeconds(2), Duration.ofSeconds(1)));
    }
}
