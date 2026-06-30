package com.performance.platform.app.config;

import com.performance.platform.application.ports.in.GetExecutionStatusUseCase;
import com.performance.platform.application.ports.in.ScenarioParsingUseCase;
import com.performance.platform.application.ports.out.ExecutionRepository;
import com.performance.platform.application.usecase.GetExecutionStatusService;
import com.performance.platform.assertion.AssertionExecutorRegistry;
import com.performance.platform.engine.local.TaskExecutorLookup;
import com.performance.platform.infrastructure.executor.TaskExecutorRegistry;
import com.performance.platform.scenario.parser.YamlScenarioParser;
import com.performance.platform.scenario.usecase.DefaultScenarioParsingService;
import com.performance.platform.scenario.validation.DefaultScenarioValidator;
import com.performance.platform.scenario.validation.ScenarioValidator;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

/**
 * Configuration d'assemblage racine (composition root mince) — ADR-026.
 *
 * <p>Ne cree QUE les beans qui ne peuvent pas etre self-wires dans leur
 * module d'origine, a savoir :
 * <ol>
 *   <li>La glue cross-module {@link TaskExecutorLookup} qui fait le pont
 *       entre {@code TaskExecutorRegistry} et {@code AssertionExecutorRegistry}</li>
 *   <li>Le cablage du module framework-free {@code platform-scenario-dsl}
 *       ({@link YamlScenarioParser}, {@link ScenarioValidator},
 *       {@link ScenarioParsingUseCase})</li>
 *   <li>Le cablage du read-model framework-free ({@link GetExecutionStatusUseCase})</li>
 * </ol>
 *
 * <p><strong>NE contient PAS</strong> :
 * <ul>
 *   <li>{@code ExecuteScenarioUseCase} / {@code CancelExecutionUseCase} —
 *       fournis par l'engine actif ({@code LocalExecutionEngine} ou
 *       {@code RemoteExecutionEngine}) qui implemente directement les ports</li>
 *   <li>{@code ExecutionConfig} / {@code AgentRegistry} —
 *       possedes par leurs modules respectifs (engine, agent-runtime)</li>
 * </ul>
 *
 * <p>Les modules {@code platform-scenario-dsl} et {@code platform-application}
 * restent framework-free : 0 annotation Spring ajoutee.
 */
@Configuration
public class RuntimeAssemblyConfiguration {

    // ========================================================================
    // (1) Glue cross-module irréductible — TaskExecutorLookup
    // ========================================================================

    /**
     * Cree le {@link TaskExecutorLookup} qui fait le pont entre les registres
     * de taches et d'assertions.
     *
     * <p>Exception assumee par ADR-026 : {@code platform-app} est le seul
     * module qui depend a la fois de {@code platform-infrastructure}
     * ({@link TaskExecutorRegistry}) et de {@code platform-assertion}
     * ({@link AssertionExecutorRegistry}).
     *
     * @param taskRegistry      le registre des TaskExecutor (Spring bean)
     * @param assertionRegistry le registre des AssertionExecutor (Spring bean)
     * @return le lookup bridge
     */
    @Bean
    public TaskExecutorLookup taskExecutorLookup(
            TaskExecutorRegistry taskRegistry,
            AssertionExecutorRegistry assertionRegistry) {
        return new RegistryTaskExecutorLookup(taskRegistry, assertionRegistry);
    }

    // ========================================================================
    // (2) Câblage du module framework-free scenario-dsl
    // ========================================================================

    /**
     * Parser YAML de scenarios. 0 dependance Spring.
     *
     * @return un {@link YamlScenarioParser} avec timeout par defaut de 5 minutes
     */
    @Bean
    public YamlScenarioParser yamlScenarioParser() {
        return new YamlScenarioParser();
    }

    /**
     * Validateur de scenarios. 0 dependance Spring.
     *
     * @return un {@link DefaultScenarioValidator}
     */
    @Bean
    public ScenarioValidator scenarioValidator() {
        return new DefaultScenarioValidator();
    }

    /**
     * Use case de parsing et validation de scenario.
     * Orchestre le parsing YAML puis la validation.
     *
     * @param parser    le parser YAML (injecte comme {@link YamlScenarioParser})
     * @param validator le validateur de scenario
     * @return le {@link ScenarioParsingUseCase} implemente par {@link DefaultScenarioParsingService}
     */
    @Bean
    public ScenarioParsingUseCase scenarioParsingUseCase(
            YamlScenarioParser parser,
            ScenarioValidator validator) {
        return new DefaultScenarioParsingService(parser, validator);
    }

    // ========================================================================
    // (3) Câblage du read-model framework-free (ISSUE-146)
    // ========================================================================

    /**
     * Use case de consultation du statut d'execution (read-model, CQRS-lean).
     * Lit uniquement depuis l'{@link ExecutionRepository}, decouple de l'engine.
     * Conditionnel a la presence d'{@link ExecutionRepository} — absent en mode
     * AGENT sans datasource.
     *
     * @param repository le repository d'execution (Spring bean conditionnel)
     * @return le {@link GetExecutionStatusUseCase} implemente par {@link GetExecutionStatusService}
     */
    @Bean
    @ConditionalOnProperty(prefix = "platform.datasources.default", name = "url")
    public GetExecutionStatusUseCase getExecutionStatusUseCase(ExecutionRepository repository) {
        return new GetExecutionStatusService(repository);
    }

    // ========================================================================
    // (4) Fallback RestClient.Builder (Spring Boot 4 n'auto-configure pas)
    // ========================================================================

    /**
     * Fallback {@link RestClient.Builder} pour les modules qui en dependent
     * (ex: {@code HttpTargetConfiguration} dans platform-infrastructure).
     * <p>
     * Spring Boot 4.0 ne fournit pas d'auto-configuration pour
     * {@code RestClient.Builder}. Ce bean est un filet de securite —
     * {@link ConditionalOnMissingBean} garantit qu'il ne remplace pas
     * un bean defini ailleurs.
     *
     * @return un {@link RestClient.Builder} par defaut
     */
    @Bean
    @ConditionalOnMissingBean
    public RestClient.Builder restClientBuilder() {
        return RestClient.builder();
    }
}
