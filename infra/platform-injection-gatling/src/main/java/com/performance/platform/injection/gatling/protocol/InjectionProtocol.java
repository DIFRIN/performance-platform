package com.performance.platform.injection.gatling.protocol;

import java.util.Map;

/**
 * SPI of the {@code gatling} step: builds, from the step parameters and the application
 * configuration, the simulation of one protocol (http, kafka, ...). Implementations are Spring
 * beans, discovered by {@link #name()} — the executor never switches on the protocol.
 */
public interface InjectionProtocol {

    /** Value of the step parameter {@code protocol} selecting this implementation (lower case). */
    String name();

    /**
     * @return the prepared injection; the caller must {@link PreparedInjection#close() close} it
     * @throws IllegalArgumentException on any invalid or inconsistent parameter
     */
    PreparedInjection prepare(String executionId, String stepId, Map<String, Object> params);
}
