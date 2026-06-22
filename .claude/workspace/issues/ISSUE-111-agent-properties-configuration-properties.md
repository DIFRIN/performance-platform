# ISSUE-111 — Create AgentProperties @ConfigurationProperties record

**PDR** : PDR-026
**Module** : `platform-app`
**Statut** : WAITING
**Priorite** : P0 (bloquant tout le cablage agent)
**Bloquee par** : aucune
**Estime** : S (< 1h)

---

## Objectif

Creer le record `AgentProperties` avec `@ConfigurationProperties(prefix = "agent")` qui lit la propriete `agent.supported-tasks` depuis le YAML et/ou la variable d'environnement `AGENT_SUPPORTED_TASKS`. Ce record est la source unique de verite pour la specialisation des agents en mode DISTRIBUTED.

## Fichiers a Creer

```
platform-app/src/main/java/com/performance/platform/app/config/
  └── AgentProperties.java           — @ConfigurationProperties record

platform-app/src/test/java/com/performance/platform/app/config/
  └── AgentPropertiesTest.java       — tests de binding YAML + env var
```

## Interfaces a Implementer

```java
package com.performance.platform.app.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/**
 * Proprietes de configuration de l'agent, prefixe "agent".
 * <p>
 * En mode DISTRIBUTED (role AGENT), la liste {@code supportedTasks} definit
 * quelles tasks l'agent est capable d'executer. L'env var
 * {@code AGENT_SUPPORTED_TASKS} (valeurs separees par des virgules) ecrase
 * cette liste via le mecanisme standard Spring Boot de binding de liste.
 * <p>
 * En mode LOCAL, cette propriete est ignoree : {@code LocalAgent} derive
 * automatiquement tous les task names du {@code TaskExecutorRegistry}.
 * <p>
 * Spring Boot mappe automatiquement la variable d'environnement
 * {@code AGENT_SUPPORTED_TASKS} (format: {@code "mock-server,http-client"})
 * vers cette liste grace au prefixe {@code agent}.
 *
 * @param supportedTasks liste des noms de task que l'agent supporte
 */
@ConfigurationProperties(prefix = "agent")
public record AgentProperties(List<String> supportedTasks) {

    /**
     * Constructeur compact avec defensive copy.
     * Si la propriete n'est pas definie, la liste est vide (pas null).
     */
    public AgentProperties {
        supportedTasks = supportedTasks != null ? List.copyOf(supportedTasks) : List.of();
    }
}
```

## Regles Specifiques

- Inclure des commentaires Javadoc expliquant le mapping env var `AGENT_SUPPORTED_TASKS` (Spring Boot le fait automatiquement grace au prefixe `agent`).
- Utiliser `@ConfigurationProperties` et non `@Value` (regle ADR-013 / CC-05 Spring-first).
- La liste est immuable (`List.copyOf` dans le constructeur compact).
- La classe est un `record` Java (pas une classe classique), suivant les conventions du projet.

## Enregistrement Spring Boot

Dans `PerformancePlatformApplication` ou une classe `@Configuration` dediee, ajouter :
```java
@ConfigurationPropertiesScan
```
ou bien :
```java
@EnableConfigurationProperties(AgentProperties.class)
```

Preferer `@EnableConfigurationProperties(AgentProperties.class)` sur la classe `AgentRuntimeConfiguration` (creee dans ISSUE-112) pour garder le scan explicite et localise.

## Tests

`AgentPropertiesTest` doit couvrir :

1. **Default empty list** : quand aucune propriete `agent.supported-tasks` n'est definie, `supportedTasks()` retourne une liste vide.
2. **Parsing from YAML** : binder `agent.supported-tasks: [mock-server, http-client]` → liste de 2 elements.
3. **Env var override** : `AGENT_SUPPORTED_TASKS=mock-server,http-client,gatling` → liste de 3 elements.
4. **Immuabilite** : la liste retournee ne peut pas etre modifiee (toute tentative leve `UnsupportedOperationException`).
5. **Single value** : binder `agent.supported-tasks: [mock-server]` → liste de 1 element.
6. **Duplicates preserves** : binder `agent.supported-tasks: [a, b, a]` → liste `[a, b, a]` (pas de deduplication -- le `Set.copyOf` sera fait par le consommateur `DefaultTaskSpecializationFilter`).

Utiliser `SpringBootTest` ou `ConfigurationPropertiesBindTest` (test de binding sans demarrer l'appli entiere, via `BindResult` de Spring Boot).

## Critères de Done

- [ ] `mvn test -pl platform-app -q` → 0 erreur
- [ ] `AgentProperties` enregistre dans Spring (verifie par test de binding)
- [ ] `AGENT_SUPPORTED_TASKS` env var mappe correctement a `agent.supported-tasks`
- [ ] `.claude/workspace/progress.md` mis a jour : ISSUE-111 → DONE
- [ ] `.claude/workspace/interfaces-registry.md` mis a jour : `AgentProperties` → STABLE
