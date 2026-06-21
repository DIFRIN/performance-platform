package com.performance.platform.infrastructure.executor.http;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration
@EnableConfigurationProperties(PlatformHttpTargetsProperties.class)
public class HttpTargetConfiguration {

    private static final Logger log = LoggerFactory.getLogger(HttpTargetConfiguration.class);

    @Bean
    public HttpTargetRegistry httpTargetRegistry(
            PlatformHttpTargetsProperties props,
            RestClient.Builder restClientBuilder) {
        HttpTargetRegistry registry = new HttpTargetRegistry(props.httpTargets(), restClientBuilder);
        if (props.httpTargets().isEmpty()) {
            log.warn("action=no_http_target_configured prefix=platform.http-targets");
        }
        return registry;
    }
}
