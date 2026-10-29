package com.performance.platform.infrastructure.executor.http;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.Map;

@ConfigurationProperties(prefix = "platform")
public record PlatformHttpTargetsProperties(
        Map<String, HttpTargetProperties> httpTargets
) {
    public PlatformHttpTargetsProperties {
        httpTargets = httpTargets != null ? Map.copyOf(httpTargets) : Map.of();
    }
}
