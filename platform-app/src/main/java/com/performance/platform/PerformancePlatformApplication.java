package com.performance.platform;

import com.performance.platform.app.cli.CliScenarioRunner;
import com.performance.platform.app.config.AgentProperties;
import com.performance.platform.app.plugin.PluginProperties;
import com.performance.platform.app.web.WebUiProperties;
import com.performance.platform.reporting.output.ReportProperties;
import com.performance.platform.runtime.RuntimeConfigResolver;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ConfigurableApplicationContext;

/**
 * Point d'entree unique de la plateforme (CF-01 : un seul JAR executable).
 * <p>
 * Les frontieres inter-modules sont verifiees par
 * {@code ModuleDependencyTest} (scan bytecode ASM, compatible Java 25).
 * Toute communication inter-module passe par {@code ApplicationEventPublisher}.
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
 * <p>
 * <b>ADR-021</b> : detection de {@code --scenario=} dans les arguments CLI
 * avant le choix du {@link WebApplicationType}. Si l'argument est present,
 * l'application demarre en mode CLI headless ({@code WebApplicationType.NONE}),
 * execute le scenario via {@link CliScenarioRunner}, imprime un resume sur
 * stdout, et sort avec un code de retour (0 succes, 1 echec, 2 args invalides).
 * Sans {@code --scenario=}, le comportement serveur reste inchange.
 */
@SpringBootApplication
@EnableConfigurationProperties({PluginProperties.class, AgentProperties.class, ReportProperties.class, WebUiProperties.class})
public class PerformancePlatformApplication {

    private static final Logger log = LoggerFactory.getLogger(PerformancePlatformApplication.class);

    public static void main(String[] args) {
        log.info("action=starting_platform");

        // ADR-021: Detect --scenario= for headless CLI mode
        if (hasScenarioArg(args)) {

            // ADR-021/ADR-019: --scenario= is invalid in AGENT mode.
            // Agent nodes receive TaskExecutionRequest via transport, never a CLI scenario.
            if (isAgentMode(args)) {
                System.err.println("ERROR: --scenario= is not supported in AGENT mode. "
                        + "AGENT nodes receive tasks via the transport layer, not the CLI.");
                System.exit(2);
            }

            log.info("action=headless_cli_mode web_server=disabled");
            ConfigurableApplicationContext ctx = new SpringApplicationBuilder(
                    PerformancePlatformApplication.class)
                    .web(WebApplicationType.NONE)
                    .run(args);

            // CliScenarioRunner has executed and stored the exit code.
            // SpringApplication.exit() closes the context and aggregates ExitCodeGenerator beans.
            int exitCode = SpringApplication.exit(ctx);
            System.exit(exitCode);
            return;
        }

        // Mode serveur (comportement existant inchange)
        SpringApplication app = new SpringApplication(PerformancePlatformApplication.class);

        // ADR-019: Agent never exposes a web server (no Tomcat)
        if (isAgentMode(args)) {
            app.setWebApplicationType(WebApplicationType.NONE);
            log.info("action=agent_mode web_server=disabled");
        }

        app.run(args);
        log.info("action=platform_ready");
    }

    /**
     * Detecte si l'instance doit tourner en mode AGENT.
     * <p>
     * Priorite (ADR-006) : variable d'environnement {@code MODE}, puis
     * propriete systeme {@code runtime.role}, puis arguments de ligne de
     * commande Spring Boot ({@code --runtime.role=AGENT} ou
     * {@code --spring.profiles.active=agent}).
     * <p>
     * Les arguments CLI doivent etre inspectes ici, avant le demarrage de
     * Spring : le {@link WebApplicationType} est choisi avant que
     * l'{@code Environment} ne soit construit (ADR-019).
     */
    private static boolean isAgentMode(String[] args) {
        return RuntimeConfigResolver.isAgent(args,
                System.getenv(RuntimeConfigResolver.ENV_MODE),
                System.getProperty(RuntimeConfigResolver.PROP_RUNTIME_ROLE));
    }

    /**
     * Fonction pure de detection du role AGENT, testable sans variables
     * globales. Delegue a {@link RuntimeConfigResolver} (source unique).
     *
     * @param args          arguments CLI Spring Boot (peut etre null)
     * @param envMode       valeur de la variable d'environnement {@code MODE}
     * @param roleProperty  valeur de la propriete systeme {@code runtime.role}
     * @return true si l'instance doit tourner en AGENT
     */
    static boolean detectAgentRole(String[] args, String envMode, String roleProperty) {
        return RuntimeConfigResolver.isAgent(args, envMode, roleProperty);
    }

    /**
     * Detecte la presence de l'argument {@code --scenario=} dans la ligne de commande.
     * ADR-021 : cette detection precede le choix du {@link WebApplicationType}.
     */
    static boolean hasScenarioArg(String[] args) {
        if (args == null) {
            return false;
        }
        for (String arg : args) {
            if (arg.startsWith("--scenario=")) {
                return true;
            }
        }
        return false;
    }
}
