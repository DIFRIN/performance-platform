package com.performance.platform.app.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/**
 * Proprietes de configuration de l'agent, prefixe {@code agent}.
 * <p>
 * En mode DISTRIBUTED (role {@code AGENT}), la liste {@code supportedTasks}
 * definit quelles tasks l'agent est capable d'executer. L'env var
 * {@code AGENT_SUPPORTED_TASKS} (valeurs separees par des virgules) ecrase
 * cette liste via le mecanisme standard Spring Boot de binding de liste.
 * <p>
 * En mode LOCAL, cette propriete est ignoree : {@code LocalAgent} derive
 * automatiquement tous les task names du {@code TaskExecutorRegistry}
 * (voir ADR-015).
 * <p>
 * Spring Boot mappe automatiquement la variable d'environnement
 * {@code AGENT_SUPPORTED_TASKS} (format: {@code "mock-server,http-client"})
 * vers cette liste grace au prefixe {@code agent} et au mecanisme
 * de relaxed binding.
 *
 * <h3>ADR-015 — Configuration-Driven Agent Specialization</h3>
 * <p>Ce record est la source unique de verite pour la specialisation des
 * agents. Les annotations {@code @Preparation}, {@code @Injection},
 * {@code @Assertion} restent utilisees par le {@code PluginLoader} pour
 * resoudre task-name → implementation, mais ne sont PLUS utilisees pour
 * deriver {@code supportedTaskNames}.
 *
 * @param supportedTasks liste des noms de task que l'agent supporte ;
 *                       vide par defaut si la propriete n'est pas definie
 */
@ConfigurationProperties(prefix = "agent")
public record AgentProperties(List<String> supportedTasks) {

    /**
     * Constructeur compact avec defensive copy et nettoyage des entrees vides.
     * Si la propriete n'est pas definie, la liste est vide (pas null).
     * Les entrees vides ou blanches (ex: env var vide) sont filtrees.
     */
    public AgentProperties {
        if (supportedTasks == null) {
            supportedTasks = List.of();
        } else {
            supportedTasks = supportedTasks.stream()
                    .filter(s -> s != null && !s.isBlank())
                    .collect(java.util.stream.Collectors.toUnmodifiableList());
        }
    }
}
