package com.performance.platform.injection.gatling.worker;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/** Enregistre {@link InjectionWorkerProperties}. */
@Configuration
@EnableConfigurationProperties(InjectionWorkerProperties.class)
public class InjectionWorkerConfiguration {
}
