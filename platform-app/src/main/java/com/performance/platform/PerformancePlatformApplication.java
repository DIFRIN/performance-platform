package com.performance.platform;

import com.performance.platform.app.config.AgentProperties;
import com.performance.platform.app.plugin.PluginProperties;
import com.performance.platform.app.web.WebUiProperties;
import com.performance.platform.reporting.output.ReportProperties;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
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
 *   <li>{@code agent} — agent distribue, aucun serveur web (ADR-019)</li>
 * </ul>
 * <p>
 * <b>ADR-019</b> : en mode AGENT ({@code MODE=AGENT} env var ou
 * {@code runtime.role=AGENT}), le {@link WebApplicationType#NONE} est force
 * dans {@code main()} avant le demarrage Spring Boot. Aucun Tomcat n'est
 * demarre : ni API REST, ni IHM, ni CLI. Cette garantie est au niveau JVM,
 * pas au niveau d'une property Spring.
 */
@SpringBootApplication
@Modulith
@EnableConfigurationProperties({PluginProperties.class, AgentProperties.class, ReportProperties.class, WebUiProperties.class})
public class PerformancePlatformApplication {

    private static final Logger log = LoggerFactory.getLogger(PerformancePlatformApplication.class);

    public static void main(String[] args) {
        log.info("action=starting_platform");
        SpringApplication app = new SpringApplication(PerformancePlatformApplication.class);

        // ADR-019: Agent never exposes a web server (no Tomcat)
        if (isAgentMode()) {
            app.setWebApplicationType(WebApplicationType.NONE);
            log.info("action=agent_mode web_server=disabled");
        }

        app.run(args);
        log.info("action=platform_ready");
    }

    /**
     * Detecte si l'instance doit tourner en mode AGENT.
     * <p>
     * Verifie d'abord la variable d'environnement {@code MODE} (ADR-006 :
     * env var prioritaire), puis la propriete systeme {@code runtime.role}.
     */
    private static boolean isAgentMode() {
        String modeEnv = System.getenv("MODE");
        if (modeEnv != null && modeEnv.equalsIgnoreCase("AGENT")) {
            return true;
        }
        String roleProp = System.getProperty("runtime.role");
        if (roleProp != null && roleProp.equalsIgnoreCase("AGENT")) {
            return true;
        }
        return false;
    }
}
