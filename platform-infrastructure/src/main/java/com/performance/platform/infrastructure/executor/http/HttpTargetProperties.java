package com.performance.platform.infrastructure.executor.http;

import java.time.Duration;
import java.util.Map;
import java.util.Objects;

public record HttpTargetProperties(
        String baseUrl,
        Duration connectionTimeout,
        Duration readTimeout,
        Map<String, String> defaultHeaders,
        Map<String, String> paths
) {
    public HttpTargetProperties {
        Objects.requireNonNull(baseUrl, "baseUrl required");
        connectionTimeout = connectionTimeout != null ? connectionTimeout : Duration.ofSeconds(2);
        readTimeout = readTimeout != null ? readTimeout : Duration.ofSeconds(5);
        defaultHeaders = defaultHeaders != null ? Map.copyOf(defaultHeaders) : Map.of();
        paths = paths != null ? Map.copyOf(paths) : Map.of();
    }
}
