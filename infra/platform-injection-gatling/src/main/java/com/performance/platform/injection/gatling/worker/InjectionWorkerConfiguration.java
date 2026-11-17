package com.performance.platform.injection.gatling.worker;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/** Enregistre {@link InjectionWorkerProperties}. */
@Configuration
@EnableConfigurationProperties({InjectionWorkerProperties.class, com.performance.platform.injection.gatling.load.InjectionLimits.class})
public class InjectionWorkerConfiguration {
}
