package com.performance.platform.infrastructure.executor.http;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public class HttpTargetRegistry {

    private static final Logger log = LoggerFactory.getLogger(HttpTargetRegistry.class);

    private final Map<String, HttpTargetProperties> targets;
    private final RestClient.Builder restClientBuilder;
    private final ConcurrentMap<String, RestClient> clientCache = new ConcurrentHashMap<>();

    public HttpTargetRegistry(Map<String, HttpTargetProperties> targets,
                               RestClient.Builder restClientBuilder) {
        this.targets = Map.copyOf(targets);
        this.restClientBuilder = Objects.requireNonNull(restClientBuilder);
        log.info("action=http_target_registry_initialized targetCount={}", this.targets.size());
    }

    public HttpTargetProperties get(String targetName) {
        return targets.get(targetName);
    }

    public RestClient clientFor(String targetName) {
        HttpTargetProperties props = get(targetName);
        if (props == null) throw new IllegalArgumentException("Unknown http-target: " + targetName);
        return clientCache.computeIfAbsent(targetName, k -> buildClient(props));
    }

    public String resolvePath(String targetName, String logicalPath) {
        HttpTargetProperties props = get(targetName);
        if (props == null) return logicalPath;
        return props.paths().getOrDefault(logicalPath, logicalPath);
    }

    private RestClient buildClient(HttpTargetProperties props) {
        var httpClient = HttpClient.newBuilder()
                .connectTimeout(props.connectionTimeout())
                .build();
        var requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(props.readTimeout());

        return restClientBuilder
                .baseUrl(props.baseUrl())
                .requestFactory(requestFactory)
                .defaultHeaders(h -> props.defaultHeaders().forEach(h::set))
                .build();
    }
}
