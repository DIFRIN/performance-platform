package com.performance.platform;

import com.performance.platform.app.config.AgentProperties;
import com.performance.platform.app.plugin.PluginProperties;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.modulith.Modulith;

/**
 * Point d'entree unique de la plateforme (CF-01 : un seul JAR executable).
 * <p>
 * L'annotation {@link Modulith} active la verification des modules
 * Spring Modulith et interdit les dependances directes entre modules —
 * toute communication inter-module doit passer par {@code ApplicationEventPublisher}.
 * <p>
 * Profils Spring actifs (via {@code MODE} env var ou {@code spring.profiles.active}) :
 * <ul>
 *   <li>{@code local} — execution locale sans transport</li>
 *   <li>{@code orchestrator} — orchestration distribuee</li>
 *   <li>{@code agent} — agent distribue</li>
 * </ul>
 */
@SpringBootApplication
@Modulith
@EnableConfigurationProperties({PluginProperties.class, AgentProperties.class})
public class PerformancePlatformApplication {

    private static final Logger log = LoggerFactory.getLogger(PerformancePlatformApplication.class);

    public static void main(String[] args) {
        log.info("action=starting_platform");
        SpringApplication.run(PerformancePlatformApplication.class, args);
        log.info("action=platform_ready");
    }
}
